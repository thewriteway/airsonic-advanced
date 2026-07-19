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

Airsonic is a free, web-based media streamer providing ubiquitous access to your music. Use it to share your music with friends, or to listen to your own music while at work. You can stream to multiple players simultaneously, for instance to one player in your kitchen and another in your living room.

**Airsonic-Advanced** is a more modern implementation of the Airsonic fork with several key performance and feature enhancements. It adds and supersedes several features in Airsonic.

**This repository (thewriteway/airsonic-advanced)** focuses on keeping Airsonic-Advanced safe for use: adding tests to prevent regressions and upgrading dependent libraries. PRs for additional features are welcome!

Features
--------
- Handles very large music collections (hundreds of gigabytes). Although optimized for MP3 streaming, it works for any audio or video format that can stream over HTTP, for instance AAC and OGG.
- On-the-fly conversion and streaming of virtually any audio format (WMA, FLAC, APE, Musepack, WavPack, Shorten, ...) via transcoder plug-ins.
- Automatic resampling to a suitable bitrate if you set an upper limit for constrained bandwidth.
- Works well as a local jukebox: intuitive web interface with search and index facilities optimized for browsing large media libraries.
- Integrated Podcast receiver, with many of the same features as you find in iTunes.
- Written in Java, so it runs on most platforms: Windows, Mac, Linux and Unix variants.

![Screenshot](contrib/assets/screenshot.png)

Installation
------------

### Stand-alone binaries

Download from the [GitHub releases page](https://github.com/thewriteway/airsonic-advanced/releases). You need a _minimum_ Java Runtime Environment (JRE) of **21**.

Please use the [Airsonic documentation](https://airsonic.github.io/docs/) for instructions on running Airsonic. For the most part Airsonic-Advanced shares similar running instructions unless stated otherwise. Notable exceptions are available as comments or resolutions in the Issues page (please search).

### Docker

Images are published to GitHub Container Registry as `ghcr.io/thewriteway/airsonic-advanced:latest`.

A minimal invocation:

```bash
docker run -d --name airsonic-advanced \
    -p 4040:4040 \
    -v /path/to/config:/var/airsonic \
    -v /path/to/music:/var/music \
    -v /path/to/playlists:/var/playlists \
    -v /path/to/podcasts:/var/podcasts \
    ghcr.io/thewriteway/airsonic-advanced:latest
```

Or with Docker Compose:

```yaml
services:
  airsonic-advanced:
    image: ghcr.io/thewriteway/airsonic-advanced:latest
    container_name: airsonic-advanced
    ports:
      - "4040:4040"
    environment:
      - PUID=1000
      - PGID=1000
    volumes:
      - /path/to/config:/var/airsonic
      - /path/to/music:/var/music
      - /path/to/playlists:/var/playlists
      - /path/to/podcasts:/var/podcasts
    restart: unless-stopped
```

#### Volumes

| Container path | Purpose |
| --- | --- |
| `/var/airsonic` | Configuration, database, logs, transcode binaries |
| `/var/music` | Default music folder |
| `/var/playlists` | Default playlist folder |
| `/var/podcasts` | Default podcast folder |

#### Ports

| Port | Purpose |
| --- | --- |
| `4040` | Web UI / API (HTTP) |
| `4041` | DLNA/UPnP streaming |
| `1900/udp` | DLNA/UPnP discovery |

#### Docker environment variables

| Variable | Default | Description |
| --- | --- | --- |
| `AIRSONIC_PORT` | `4040` | Port the server listens on inside the container |
| `AIRSONIC_DIR` | `/var` | Base directory; config, music, playlists and podcasts live under `$AIRSONIC_DIR/airsonic`, `$AIRSONIC_DIR/music`, `$AIRSONIC_DIR/playlists`, `$AIRSONIC_DIR/podcasts` |
| `CONTEXT_PATH` | `/` | Servlet context path (set when hosting under a sub-path behind a proxy) |
| `UPNP_PORT` | `4041` | Port used for DLNA/UPnP streaming |
| `PUID` | `0` | User ID the application runs as (created and switched to via `gosu` if the container starts as root) |
| `PGID` | `0` | Group ID the application runs as |
| `JAVA_OPTS` | _(empty)_ | Extra JVM arguments, e.g. `-Dserver.forward-headers-strategy=native` when running behind a reverse proxy |
| `AIRSONIC_ADMIN_PASSWORD` | _(unset)_ | Sets the initial `admin` password on first startup; if unset, a strong random password is generated and written to the logs |

The container also accepts any of the application [configuration environment variables](docs/configures/detail.md) (e.g. `AIRSONIC_SCAN_PARALLELISM`, `AIRSONIC_CUE_ENABLED`).

For jukebox (audio output on the server) inside Docker, see [Jukebox](docs/media/jukebox.md).

### Building from source

You may compile the code yourself using Maven. One of the repositories does not have https, so you may need to allow that for Maven; a custom `settings.xml` has been put in the `.mvn` folder for this purpose. A sample invocation (in the root):

```
mvn clean compile package verify
```

The main binary will be in `airsonic-main/target`.

Getting started
---------------

### Default admin account

On first install (including Docker) an `admin` account is created automatically. Unless you set the password explicitly via the `AIRSONIC_ADMIN_PASSWORD` environment variable before the first startup, a strong random password is generated and shown in the console/docker logs. Change it afterwards under `Settings` > `Users`.

See the [First start guide](docs/first_start/README.md) for recommended setup steps (running as a dedicated user, creating accounts, adding media folders).

### Media folders

Airsonic organizes your music according to how it is organized on disk (not by embedded tags, although tags are read for presentation and search). It is recommended that music folders are organized in an **"artist/album/song"** manner. Add folders under `Settings` > `Media folders`. See [how media is categorized](docs/media/rule.md).

Documentation
-------------

Full documentation lives in the [docs](docs/README.md) folder:

- **[First start](docs/first_start/README.md)** — process user, user accounts, media folders
- **Web UI**
  - [Media](docs/webui/media.md) — browsing, artist view, editing artist names
  - [Podcast](docs/webui/podcast.md) — podcast channel management, episode locking and downloading
  - [Lyrics](docs/webui/lyrics.md) — lyrics from chartlyrics.com, LRC files, and manual input
- **[Configuration](docs/configures/README.md)** — Java options, environment variables, `airsonic.properties`, web interface
  - [Detail configuration](docs/configures/detail.md) — all `airsonic.*` options with defaults and matching environment variables
- **Reverse proxy**
  - [Prerequisites](docs/proxy/README.md) — TLS, forward headers, expected `X-Forwarded-*` headers
  - [Apache](docs/proxy/apache.md); for [Nginx](https://airsonic.github.io/docs/proxy/nginx/), [HAProxy](https://airsonic.github.io/docs/proxy/haproxy) and [Caddy](https://airsonic.github.io/docs/proxy/caddy) see the upstream Airsonic docs
- **Media**
  - [Rule](docs/media/rule.md) — how directories and files are categorized into Artist/Album/Song/Video
  - [Cover art / artist image](docs/media/coverart.md) — sources, quality and concurrency settings
  - [Jukebox](docs/media/jukebox.md) — playing audio on the server, incl. systemd, Docker and PulseAudio setups
- **[Troubleshooting](docs/troubleshooting.md)** — cue sheet playback, blank pages / mixed content over HTTPS

Reverse proxy notes
-------------------

Airsonic-Advanced communicates with its Web UI via websockets. If you're behind a proxy, you need to enable websockets and allow UPGRADE http requests through the proxy. A sample configuration is posted here: [nginx sample](https://github.com/airsonic-advanced/airsonic-advanced/issues/145).

Additionally, the server needs to forward headers, for which the following property is necessary (in `/path/to/airsonic-data/airsonic.properties`, as a JVM argument, or via `JAVA_OPTS` in Docker):

```
server.forward-headers-strategy=native
```

(`framework` is also accepted; see the [proxy prerequisites](docs/proxy/README.md) for details.)

Compatibility notes
-------------------

The following properties are new in Airsonic-Advanced:
- `AIRSONIC_SCAN_PARALLELISM`: (default: number of available processors + 1) The parallelism to use when scanning media (replaces the deprecated `MediaScannerParallelism` from versions <= 11.1.2)
- `ClearFullScanSettingAfterScan`: (default: false) Whether to clear the FullScan setting after the next SUCCESSFUL scan (useful for doing a full scan once and then reverting to the default scan)

The following property names are different between Airsonic and Airsonic-Advanced:
- `UPNP_PORT` -> `UPnpPort`
- `server.context-path` -> `server.servlet.context-path`
- `IgnoreFileTimestamps` -> `FullScan`

License
-------

Airsonic-Advanced and Airsonic are free software and licensed under the [GNU General Public License version 3](http://www.gnu.org/copyleft/gpl.html). The code in this repository (and associated binaries) are free of any "license key" or other restrictions. If you wish to thank the maintainer of this repository, please consider a donation to the [Electronic Frontier Foundation](https://supporters.eff.org/donate).

The [Subsonic source code](https://github.com/airsonic/subsonic-svn) was released under the GPLv3 through version 6.0-beta1. Beginning with 6.0-beta2, source is no longer provided. Binaries of Subsonic are only available under a commercial license. There is a [Subsonic Premium](http://www.subsonic.org/pages/premium.jsp) service which adds functionality not available in Airsonic. Subsonic also offers RPM, Deb, Exe, and other pre-built packages that Airsonic [currently does not](https://github.com/airsonic/airsonic/issues/65).

The cover zooming feature is provided by [jquery.fancyzoom](https://github.com/keegnotrub/jquery.fancyzoom), released under [MIT License](http://www.opensource.org/licenses/mit-license.php).

The icons are from the amazing [feather](https://feathericons.com/) project, and are licensed under [MIT license](https://github.com/feathericons/feather/blob/master/LICENSE).

The cover art functionality supporting multiple image file formats is powered by the [TwelveMonkeys](https://github.com/haraldk/TwelveMonkeys) library, which is released under the [BSD3 License](https://github.com/haraldk/TwelveMonkeys/blob/main/LICENSE.md).

Community
---------

Bugs, feature requests and discussions pertaining to thewriteway/airsonic-advanced may be raised as issues on the [project's GitHub page](https://github.com/thewriteway/airsonic-advanced/issues).
