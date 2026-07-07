<!--
# README.md
# thewriteway/airsonic-advanced
-->
Airsonic-Advanced
=================
![Release](https://shieldcn.dev/github/release/thewriteway/airsonic-advanced)
![Downloads](https://shieldcn.dev/github/downloads/thewriteway/airsonic-advanced.svg)
![Stars](https://shieldcn.dev/github/stars/thewriteway/airsonic-advanced)
![Forks](https://shieldcn.dev/github/forks/thewriteway/airsonic-advanced)
![Commits](https://shieldcn.dev/github/commits/thewriteway/airsonic-advanced)
[![CodeQL](https://github.com/thewriteway/airsonic-advanced/actions/workflows/codeql.yml/badge.svg)](https://github.com/thewriteway/airsonic-advanced/actions/workflows/codeql.yml)


What is thewriteway/airsonic-advanced?
------------------------------------
The main objective of this repository is to keep airsonic-advanced safe for use.
I don't have much time to add features right now as I am adding tests to prevent deggregation and upgrade dependent libraries.
Therefore, PRs for additional features are welcome!

What is Airsonic-Advanced?
--------------------------
Airsonic-Advanced is a more modern implementation of the Airsonic fork with several key performance and feature enhancements. It adds and supersedes several features in Airsonic.

What is Airsonic?
-----------------

Airsonic is a free, web-based media streamer, providing ubiquitous access to your music. Use it to share your music with friends, or to listen to your own music while at work. You can stream to multiple players simultaneously, for instance to one player in your kitchen and another in your living room.

Airsonic is designed to handle very large music collections (hundreds of gigabytes). Although optimized for MP3 streaming, it works for any audio or video format that can stream over HTTP, for instance AAC and OGG. By using transcoder plug-ins, Airsonic supports on-the-fly conversion and streaming of virtually any audio format, including WMA, FLAC, APE, Musepack, WavPack and Shorten.

If you have constrained bandwidth, you may set an upper limit for the bitrate of the music streams. Airsonic will then automatically resample the music to a suitable bitrate.

In addition to being a streaming media server, Airsonic works very well as a local jukebox. The intuitive web interface, as well as search and index facilities, are optimized for efficient browsing through large media libraries. Airsonic also comes with an integrated Podcast receiver, with many of the same features as you find in iTunes.

Written in Java, Airsonic runs on most platforms, including Windows, Mac, Linux and Unix variants.

![Screenshot](contrib/assets/screenshot.png)


The complete list of PRs that were used to enhance Airsonic can be seen on the PRs page. At some point an automatic changelog generator will be added to keep track.

Airsonic-Advanced will occasionally backport features introduced in the base Airsonic fork, but is generally much more modern and bleeding edge than Airsonic.

Usage
-----
### Stand-alone binaries
Airsonic-Advanced can be downloaded from
[GitHub](https://github.com/thewriteway/airsonic-advanced/releases).

You need a _minimum_ Java Runtime Environment (JRE) of 21

Airsonic-Advanced is run similarly to (and in lieu of) vanilla Airsonic.

Vanilla Airsonic can be downloaded from
[GitHub](https://github.com/airsonic/airsonic/releases).

Please use the [Airsonic documentation](https://airsonic.github.io/docs/) for instructions on running Airsonic. For the most part (currently) Airsonic-Advanced shares similar running instructions unless stated otherwise. Notable exceptions are available as comments or resolutions in the Issues page (please search).

### Building/Compiling
You may compile the code yourself by using maven. One of the repositories does not have https, so you may need to allow that for maven. A custom `settings.xml` has been put in `.mvn` folder for this purpose. A sample invocation would be (in the root):
```
mvn clean compile package verify
```
The main binary would be in `airsonic-main/target`

### Configuration

See the [Configuration](./docs/configures/README.md)

Compatibility Notes:
------
The following properties are new in Airsonic-Advanced:
  - `MediaScannerParallelism(<= 11.1.2)`: (default: number of available processors + 1) The parallelism to use when scanning media
  - `AIRSONIC_SCAN_PARALLELISM(> 11.1.2)`: (default: number of available processors + 1) The parallelism to use when scanning media
  - `ClearFullScanSettingAfterScan`: (default: false) Whether to clear FullScan setting after the next SUCCESSFUL scan (useful for doing full scan once and then reverting to default scan)

The following property names are different between Airsonic and Airsonic-Advanced:
  - `UPNP_PORT` -> `UPnpPort`
  - `server.context-path` -> `server.servlet.context-path` (Airsonic will use the latter from 11.0 onwards)
  - `IgnoreFileTimestamps` -> `FullScan`

Note that Airsonic-Advanced communicates with its Web UI via websockets. If you're behind a proxy, you need to enable websockets and allow UPGRADE http requests through the proxy. A sample configuration is posted here: [nginx sample](https://github.com/airsonic-advanced/airsonic-advanced/issues/145).

Additionally, if placed behind a proxy, the Airsonic server needs to forward headers, for which the following property is necessary (either in `/path/to/airsonic-data/airsonic.properties` or as a jvm argument):
  - After and including *Edge Release 11.0.0-SNAPSHOT.20210117214044*: `server.forward-headers-strategy=native`
  - Prior to *Edge Release 11.0.0-SNAPSHOT.20210117214044*: `server.use-forward-headers=true`

Troubleshooting
------

See the [Troubleshooting](./docs/troubleshooting.md)

License
-------

Airsonic-Advanced and Airsonic are free software and licensed under the [GNU General Public License version 3](http://www.gnu.org/copyleft/gpl.html). The code in this repository (and associated binaries) are free of any "license key" or other restrictions. If you wish to thank the maintainer of this repository, please consider a donation to the [Electronic Frontier Foundation](https://supporters.eff.org/donate).

The [Subsonic source code](https://github.com/airsonic/subsonic-svn) was released under the GPLv3 through version 6.0-beta1. Beginning with 6.0-beta2, source is no longer provided. Binaries of Subsonic are only available under a commercial license. There is a [Subsonic Premium](http://www.subsonic.org/pages/premium.jsp) service which adds functionality not available in Airsonic. Subsonic also offers RPM, Deb, Exe, and other pre-built packages that Airsonic [currently does not](https://github.com/airsonic/airsonic/issues/65).

The cover zooming feature is provided by [jquery.fancyzoom](https://github.com/keegnotrub/jquery.fancyzoom),
released under [MIT License](http://www.opensource.org/licenses/mit-license.php).

The icons are from the amazing [feather](https://feathericons.com/) project,
and are licensed under [MIT license](https://github.com/feathericons/feather/blob/master/LICENSE).

The cover art functionality supporting multiple image file formats is powered by the [TwelveMonkeys](https://github.com/haraldk/TwelveMonkeys) library, which is released under the [BSD3 License](https://github.com/haraldk/TwelveMonkeys/blob/main/LICENSE.md).  

Community
---------
Bugs/feature requests/discussions pertaining to kagemomiji/airsonic-advanced may be raised as issues within GitHub on the Airsonic-Advanced project page.


