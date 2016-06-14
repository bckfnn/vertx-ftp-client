# actioner

__vertx-ftp-client__ is a simple limited async ftp client which works with vert.x 

## Release

Releases management are done with [jgit-flow](https://bitbucket.org/atlassian/jgit-flow/wiki/Home) and deployment to done to bintray's jcenter.

Step to perform a release:

1. develop new features on the `develop` branch
2. `mvn jgitflow:release-start`
3. `mvn jgitflow:release-finish`
4. In eclipse do `Team/Remote/Push`, `Next`, `Next`, `Finish`
