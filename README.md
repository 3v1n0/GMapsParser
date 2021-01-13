### Simple Google Maps Navigation Notifications parser API for Android

A simple library (including a test service and application) written in kotlin
that allows to parse the [Google Maps android app](https://play.google.com/store/apps/details?id=com.google.android.apps.maps)
turn-by-turn navigation notifications in order to get textual navigation data
that can be exposed to other devices (such as wearables) or used in other
applications.

#### API overview

Using the base class (`NavigationListener`) it's possible to create a simple service that monitors
navigation events by reading the Navigation app notifications, such navigation updates are exposed
with a [NavigationData structure](navparser/src/main/java/me/trevi/navparser/lib/NavigationData.kt)
during `onNavigationNotificationAdded()` (on first event) and `onNavigationNotificationUpdated()`
(afterwards).

A `NavigationListenerEmitter` is also provided as a convenience in case such events needs to be
passed to another android service or application using Bundles or Parcelables.

Navigation events can also be exposed via a websocket that can be used by remote applications to
get the notification events and control the navigation (stop or set a destination).

#### Example application

Here is provided a sample application that will get the notifications from the
service and will replicate the parsed data in the main screen.

<img src="screenshot/Screenshot_1601854256.png" alt="Screenshot" width="250">


### Usage

You can easily use these libraries using [jitpack](https://jitpack.io/#3v1n0/GMapsParser/).

For example:

```gradle
dependencies {
    // ...
    implementation 'com.github.3v1n0.GMapsParser:navparser:master-SNAPSHOT'
    // If you want to expose the events via a websocket as JSON or CBOR events
    implementation 'com.github.3v1n0.GMapsParser:navparser-websocket:master-SNAPSHOT'
    // Needed only to replicate the UI or for debugging reasons
    implementation 'com.github.3v1n0.GMapsParser:navparser-activity:master-SNAPSHOT'
    // ...
}
```

[![](https://jitpack.io/v/3v1n0/GMapsParser.svg)](https://jitpack.io/#3v1n0/GMapsParser)


#### LICENSE

This is released under the terms of [LGPL-3.0](LICENSE.md)
