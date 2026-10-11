#!/bin/bash

set -e

echo "Process will run as:"
echo "User: $(id -u)"
echo "Group: $(id -g)"
mkdir -p $AIRSONIC_DIR/airsonic/transcode
[[ ! -f $AIRSONIC_DIR/airsonic/transcode/ffmpeg ]] && ln -fs /usr/bin/ffmpeg $AIRSONIC_DIR/airsonic/transcode/ffmpeg
[[ ! -f $AIRSONIC_DIR/airsonic/transcode/lame ]] && ln -fs /usr/bin/lame $AIRSONIC_DIR/airsonic/transcode/lame
java --version
echo "JAVA_OPTS=$JAVA_OPTS"
$AIRSONIC_DIR/airsonic/transcode/ffmpeg -version
curl --version
echo "PATH=$PATH"
echo "CONTEXT_PATH=$CONTEXT_PATH"
if [[ $# -lt 1 ]] || [[ ! "$1" == "java"* ]]; then

    java_opts_array=()
    while IFS= read -r -d '' item; do
        java_opts_array+=( "$item" )
    done < <([[ $JAVA_OPTS ]] && xargs printf '%s\0' <<<"$JAVA_OPTS")

    # JVM flags that depend on the runtime's Java version; JAVA_OPTS comes later and can override them.
    # --enable-native-access: JDK 24+ warns on (and will later deny) restricted native calls such as
    # JNA and Lucene's madvise support. Valid since JDK 17, so it's always passed.
    jdk_opts_array=( --enable-native-access=ALL-UNNAMED )
    java_feature_version=$(java -XshowSettings:properties -version 2>&1 | awk '/java.specification.version/ {print $3}')
    if [[ $java_feature_version =~ ^[0-9]+$ ]] && (( java_feature_version >= 25 )); then
        # JEP 519 (product feature in JDK 25): 8-byte object headers reduce heap use for small objects.
        jdk_opts_array+=( -XX:+UseCompactObjectHeaders )
    fi
    echo "JDK_OPTS=${jdk_opts_array[*]}"

    exec java -Xmx512m \
     -cp /app/WEB-INF/classes:/app/WEB-INF/lib/*:/app/WEB-INF/lib-provided/* \
     -Dserver.address=0.0.0.0 \
     -Dserver.port=$AIRSONIC_PORT \
     -Dserver.servlet.context-path=$CONTEXT_PATH \
     -Dairsonic.home=$AIRSONIC_DIR/airsonic \
     -Dairsonic.defaultMusicFolder=$AIRSONIC_DIR/music \
     -Dairsonic.defaultPodcastFolder=$AIRSONIC_DIR/podcasts \
     -Dairsonic.defaultPlaylistFolder=$AIRSONIC_DIR/playlists \
     -DUPnpPort=$UPNP_PORT \
     -Djava.awt.headless=true \
     "${jdk_opts_array[@]}" \
     "${java_opts_array[@]}" \
     org.airsonic.player.Application "$@"
fi

exec "$@"
