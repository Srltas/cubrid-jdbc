#!/bin/bash
#
#  Copyright 2016 CUBRID Corporation
#

# Release script for CUBRID JDBC Driver
# Builds this commit with Gradle and uploads it to the Central Portal, where the deployment waits
# for Publish or Drop. The Portal token and the signing key come from ~/.gradle/gradle.properties.

set -e
cd "$(dirname "$0")"

./gradlew -I gradle/central-publish.init.gradle publishToSonatype closeSonatypeStagingRepository
