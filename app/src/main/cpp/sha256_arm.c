// Native SHA-256d scanning kernel on the ARMv8 crypto extensions (SHA256H / SHA256H2 / SHA256SU0 / SHA256SU1).
// The same preparation as the Kotlin kernel: the midstate of each header version is computed once per job,
// the second header block (nonce, padding) and the second hash are done here per nonce. The extensions
// work on whole 4-round groups, so there is no early exit; the hash is finished and H7 is tested for 16
// low zero bits (the top 16 bits of the hash as a little-endian number).
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#if defined(HAVE_ARM_SHA2) && defined(__aarch64__)
#include <arm_neon.h>
#include <sys/auxv.h>
#include <asm/hwcap.h>

static const uint32_t K[64] = {
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2};

static const uint32_t IV[8] = {0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
                               0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19};

#define MAX_VERSIONS 8

typedef struct {
    int versions;
    uint32x4_t mid0[MAX_VERSIONS], mid1[MAX_VERSIONS];  // midstate abcd / efgh per version
    uint32_t w0, w1, w2;                                 // tail of the header: merkle root end, time, bits
} NativeJob;

// Four rounds of SHA-256 with the message words in w (already added to K)
#define ROUNDS4(s0, s1, kw)                        \
    do {                                           \
        uint32x4_t t_ = s0;                        \
        s0 = vsha256hq_u32(s0, s1, kw);            \
        s1 = vsha256h2q_u32(s1, t_, kw);           \
    } while (0)

// One compression of a 16-word block into (s0, s1); the schedule is expanded with the SU instructions
static inline void compress_block(uint32x4_t *st0, uint32x4_t *st1, uint32x4_t m0, uint32x4_t m1, uint32x4_t m2,
                                  uint32x4_t m3) {
    uint32x4_t s0 = *st0, s1 = *st1;
    uint32x4_t w0 = m0, w1 = m1, w2 = m2, w3 = m3, kw;
    for (int t = 0; t < 64; t += 16) {
        kw = vaddq_u32(w0, vld1q_u32(K + t));
        ROUNDS4(s0, s1, kw);
        kw = vaddq_u32(w1, vld1q_u32(K + t + 4));
        ROUNDS4(s0, s1, kw);
        kw = vaddq_u32(w2, vld1q_u32(K + t + 8));
        ROUNDS4(s0, s1, kw);
        kw = vaddq_u32(w3, vld1q_u32(K + t + 12));
        ROUNDS4(s0, s1, kw);
        if (t < 48) {
            w0 = vsha256su1q_u32(vsha256su0q_u32(w0, w1), w2, w3);
            w1 = vsha256su1q_u32(vsha256su0q_u32(w1, w2), w3, w0);
            w2 = vsha256su1q_u32(vsha256su0q_u32(w2, w3), w0, w1);
            w3 = vsha256su1q_u32(vsha256su0q_u32(w3, w0), w1, w2);
        }
    }
    *st0 = vaddq_u32(*st0, s0);
    *st1 = vaddq_u32(*st1, s1);
}

static int has_sha2(void) {
    return (getauxval(AT_HWCAP) & HWCAP_SHA2) != 0;
}

// Message schedule of a 16-word block with K already added: 16 vectors of 4 rounds each
static inline void schedule16(uint32x4_t m0, uint32x4_t m1, uint32x4_t m2, uint32x4_t m3, uint32x4_t kw[16]) {
    uint32x4_t w0 = m0, w1 = m1, w2 = m2, w3 = m3;
    for (int t = 0; t < 16; t += 4) {
        kw[t] = vaddq_u32(w0, vld1q_u32(K + 4 * t));
        kw[t + 1] = vaddq_u32(w1, vld1q_u32(K + 4 * t + 4));
        kw[t + 2] = vaddq_u32(w2, vld1q_u32(K + 4 * t + 8));
        kw[t + 3] = vaddq_u32(w3, vld1q_u32(K + 4 * t + 12));
        if (t < 12) {
            w0 = vsha256su1q_u32(vsha256su0q_u32(w0, w1), w2, w3);
            w1 = vsha256su1q_u32(vsha256su0q_u32(w1, w2), w3, w0);
            w2 = vsha256su1q_u32(vsha256su0q_u32(w2, w3), w0, w1);
            w3 = vsha256su1q_u32(vsha256su0q_u32(w3, w0), w1, w2);
        }
    }
}

// 64 rounds from a ready schedule
static inline void rounds1(uint32x4_t *st0, uint32x4_t *st1, const uint32x4_t kw[16]) {
    uint32x4_t s0 = *st0, s1 = *st1;
    for (int t = 0; t < 16; ++t) {
        uint32x4_t tmp = s0;
        s0 = vsha256hq_u32(s0, s1, kw[t]);
        s1 = vsha256h2q_u32(s1, tmp, kw[t]);
    }
    *st0 = vaddq_u32(*st0, s0);
    *st1 = vaddq_u32(*st1, s1);
}

// Scans count W3 words from startW (nonce = bswap32(word)) for every version; writes candidates
// (word, version) pairs into out, at most max pairs. Returns the number of candidates.
// The schedule of the second header block depends only on the nonce and is built once per nonce
// for all versions. (Interleaving two hash chains was measured slower on the A53: two schedules
// take all 32 NEON registers and spill.)
static int scan_range(const NativeJob *job, uint32_t startW, uint32_t count, uint32_t *out, int max) {
    int found = 0;
    const uint32x4_t pad1 = {0x80000000u, 0, 0, 0};
    const uint32x4_t zero = vdupq_n_u32(0);
    const uint32x4_t pad3 = {0, 0, 0, 640};
    const uint32x4_t xpad2 = {0x80000000u, 0, 0, 0};
    const uint32x4_t xpad3 = {0, 0, 0, 256};
    const uint32x4_t iv0 = vld1q_u32(IV), iv1 = vld1q_u32(IV + 4);
    uint32x4_t kw[16], kx[16];
    for (uint32_t i = 0; i < count; ++i) {
        const uint32_t w3 = startW + i;
        const uint32x4_t m0 = {job->w0, job->w1, job->w2, w3};
        schedule16(m0, pad1, zero, pad3, kw);
        for (int v = 0; v < job->versions; ++v) {
            uint32x4_t s0 = job->mid0[v], s1 = job->mid1[v];
            rounds1(&s0, &s1, kw);
            // Second hash: the digest (s0, s1) as message, then padding
            schedule16(s0, s1, xpad2, xpad3, kx);
            uint32x4_t h0 = iv0, h1 = iv1;
            rounds1(&h0, &h1, kx);
            const uint32_t h7 = vgetq_lane_u32(h1, 3);
            if ((h7 & 0xffff) == 0) {
                if (found < max) {
                    out[2 * found] = w3;
                    out[2 * found + 1] = (uint32_t)v;
                }
                ++found;
            }
        }
    }
    return found < max ? found : max;
}

static uint32_t be32(const uint8_t *p) {
    return (uint32_t)p[0] << 24 | (uint32_t)p[1] << 16 | (uint32_t)p[2] << 8 | p[3];
}

JNIEXPORT jboolean JNICALL Java_io_github_vgdotnet_dayboard_mining_NativeKernel_available(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    return has_sha2() ? JNI_TRUE : JNI_FALSE;
}

// headers: versions * 80 bytes, headers of every version (nonce 0)
JNIEXPORT jlong JNICALL Java_io_github_vgdotnet_dayboard_mining_NativeKernel_prepare(JNIEnv *env, jclass cls,
                                                                                    jbyteArray headers, jint versions) {
    (void)cls;
    if (versions < 1 || versions > MAX_VERSIONS) return 0;
    NativeJob *job = (NativeJob *)calloc(1, sizeof(NativeJob));
    if (!job) return 0;
    job->versions = versions;
    jbyte *bytes = (*env)->GetByteArrayElements(env, headers, NULL);
    const uint8_t *h = (const uint8_t *)bytes;
    job->w0 = be32(h + 64);
    job->w1 = be32(h + 68);
    job->w2 = be32(h + 72);
    for (int v = 0; v < versions; ++v) {
        const uint8_t *hv = h + 80 * v;
        uint32_t m[16];
        for (int k = 0; k < 16; ++k) m[k] = be32(hv + 4 * k);
        uint32x4_t s0 = vld1q_u32(IV), s1 = vld1q_u32(IV + 4);
        compress_block(&s0, &s1, vld1q_u32(m), vld1q_u32(m + 4), vld1q_u32(m + 8), vld1q_u32(m + 12));
        job->mid0[v] = s0;
        job->mid1[v] = s1;
    }
    (*env)->ReleaseByteArrayElements(env, headers, bytes, JNI_ABORT);
    return (jlong)(intptr_t)job;
}

JNIEXPORT void JNICALL Java_io_github_vgdotnet_dayboard_mining_NativeKernel_release(JNIEnv *env, jclass cls, jlong handle) {
    (void)env; (void)cls;
    free((void *)(intptr_t)handle);
}

// out: pairs (W3 word, version); returns the number of pairs written
JNIEXPORT jint JNICALL Java_io_github_vgdotnet_dayboard_mining_NativeKernel_scan(JNIEnv *env, jclass cls, jlong handle,
                                                                                jint startW, jint count, jintArray out) {
    (void)cls;
    const NativeJob *job = (const NativeJob *)(intptr_t)handle;
    if (!job || count <= 0) return 0;
    const int max = (*env)->GetArrayLength(env, out) / 2;
    uint32_t *buf = (uint32_t *)(*env)->GetIntArrayElements(env, out, NULL);
    const int n = scan_range(job, (uint32_t)startW, (uint32_t)count, buf, max);
    (*env)->ReleaseIntArrayElements(env, out, (jint *)buf, 0);
    return n;
}

#else  // not arm64 with crypto: stubs, the app uses the Kotlin kernel

JNIEXPORT jboolean JNICALL Java_io_github_vgdotnet_dayboard_mining_NativeKernel_available(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    return JNI_FALSE;
}
JNIEXPORT jlong JNICALL Java_io_github_vgdotnet_dayboard_mining_NativeKernel_prepare(JNIEnv *env, jclass cls, jbyteArray headers, jint versions) {
    (void)env; (void)cls; (void)headers; (void)versions;
    return 0;
}
JNIEXPORT void JNICALL Java_io_github_vgdotnet_dayboard_mining_NativeKernel_release(JNIEnv *env, jclass cls, jlong handle) {
    (void)env; (void)cls; (void)handle;
}
JNIEXPORT jint JNICALL Java_io_github_vgdotnet_dayboard_mining_NativeKernel_scan(JNIEnv *env, jclass cls, jlong handle, jint startW, jint count, jintArray out) {
    (void)env; (void)cls; (void)handle; (void)startW; (void)count; (void)out;
    return 0;
}

#endif
