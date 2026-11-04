# DayBoard

Personal Android dashboard: clocks in several time zones with a working-hours strip, today's
Google Calendar events and tasks, unread counters for Gmail and Slack with a blinking indicator,
and a 4×2 home-screen widget.

## Stack

- Kotlin, Jetpack Compose, Material 3; dark and light theme follow the system setting.
- Phone: one "Today" screen with bottom navigation (Today / Inbox / Settings).
- Tablet in landscape (width ≥ 840 dp): side rail and two columns, a Google Calendar style
  day view on the left and the unread feed on the right.
- Data sources (planned): Google Calendar API, Google Tasks API, Gmail API (counter and latest
  messages only), Slack API. Tapping a message opens it in Gmail or Slack; the app has no mail
  reader of its own. Widget: Glance.

The design reference lives in `docs/mockup/` (screens exported from the design artifact) and
`docs/DESIGN.md` (screens, colour tokens of both themes, typography, blinking rules).

## Build

Requires JDK 17+ and the Android SDK (platform 36). Set `sdk.dir` in `local.properties`, then:

```
gradlew assembleDebug
```

The debug build uses the application id `io.github.vgdotnet.dayboard.dev` and currently runs on
test data from `FakeDashboardRepository`, anchored to the current time.

## Layout

```
app/src/main/java/io/github/vgdotnet/dayboard/
  MainActivity.kt           edge-to-edge activity, applies the theme
  model/                    zones, events, tasks, inbox items, settings
  data/                     DashboardRepository + FakeDashboardRepository
  ui/DashboardViewModel.kt  state, ticking clock, task / read / settings actions
  ui/DayBoardApp.kt         phone and tablet shells, navigation
  ui/theme/                 colour tokens for both themes, Material 3 mapping
  ui/components/            cards, segmented filter, blink / ping indicators
  ui/today/                 clocks, working hours, agenda, unread block, day view
  ui/inbox/                 inbox list (phone screen and tablet column)
  ui/settings/              zones, accounts, notifications, poll interval
  ui/tablet/                tablet "Today" screen
```
