# MPEG 1 Video and MP2 Audio

## Features

The following features are targeted for this project:

* [x] MPEG-TS demuxing
* [] MPEG-TS muxing
* [] MPEG1 video encode
* [] MPEG1 video decode
* [] MP2 audio encode
* [] MP2 audio decode
* [] WebSocket push

## Prerequisites

### Linux

SDL2 and OpenGL are needed for the player test app `pl_mpeg_player.c`; they are not needed to build the dist files. The player code is disabled in the pom so to build it, you must use your compiler directly; I recommend VS Code.

```sh
sudo apt install libglm-dev libglew-dev libsdl2-dev
```

### Windows

[GLEW](https://www.opengl.org/sdk/libs/GLEW/)

[SDL2](https://www.libsdl.org/download-2.0.php)

## Test

To perform a quick test, after a successful build, execute the following:

```sh
java -Djava.library.path=target/nar/red5-mpeg-1.0.0-SNAPSHOT-amd64-Linux-gpp-jni/lib/amd64-Linux-gpp/jni -cp target/lib/slf4j-api-1.7.25.jar:target/lib/logback-core-1.2.3.jar:target/lib/logback-classic-1.2.3.jar:target/red5-mpeg-1.0.0-SNAPSHOT.jar org.red5.mpeg.Main TestRun testBytes
```

_Ensure that the slf4j and logback jars are in the `lib` directory and update versions as needed_

Test with `ts` file:

```sh
java -Djava.library.path=target/nar/red5-mpeg-1.0.0-SNAPSHOT-amd64-Linux-gpp-jni/lib/amd64-Linux-gpp/jni -cp target/lib/slf4j-api-1.7.25.jar:target/lib/logback-core-1.2.3.jar:target/lib/logback-classic-1.2.3.jar:target/red5-mpeg-1.0.0-SNAPSHOT.jar org.red5.mpeg.Main TestRun src/main/resources/bars.ts
```

## References

 * [Red5 Ticket](https://github.com/Red5/red5-server/issues/283)
 * [JsMpeg](https://jsmpeg.com/)
 * [JsMpeg - phoboslab](https://github.com/phoboslab/jsmpeg)
 * [Mpeg decoder - phoboslab](https://github.com/phoboslab/pl_mpeg)
 * [MPEG-TS - Unit-X](https://github.com/Unit-X/mpegts)
 * [JNI Guide](https://devarea.com/java-and-cc-jni-guide/)