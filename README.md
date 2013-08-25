maxine-mirror
=============

A git snapshot of https://kenai.com/hg/maxine~maxine

This repo has a modified verifier that will report linkage errors in addition to verify errors.
The purpose is to statically check binary compatibility of jars.

```
mxtool/mx build
mxtool/mx --cp-pfx <classpath> verify <method patterns>
```
