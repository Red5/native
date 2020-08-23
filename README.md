# native

Native support

## NDI Producer

Utilizes the NewTek NDI SDK to produce a usable NDI source from a ClientBroadcastStream with Red5 Server. In the first build, the stream must use h.264 for video and AAC for audio; more codecs may be supported at a later time.

## MPEG

MPEG1 and MP2 codec handling (decode, encode, demux, and transcode support). The primary objective here is to provide support for players that are not flash-based such as [jsmpeg](https://jsmpeg.com/).

[JSMpeg.Player](https://github.com/phoboslab/jsmpeg)
[PL_MPEG source](https://github.com/phoboslab/pl_mpeg)

Thanks to Dominic Szablewski for the original decoder / demuxer source.

## HEVC

**arch/design stage**


## Licenses

Licenses are included in the source unless otherwise specified in the readme file or located in the subdirectory for the project section. The overall license is APL 2.0.
