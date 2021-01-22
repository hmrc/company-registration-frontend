#!/usr/bin/env bash

sbt "run 9970 -Dapplication.router=testOnlyDoNotUseInAppConf.Routes -Dconfig.resource=application.conf -Dfeature.paye=2017-06-05T15:00:00Z_X -Dfeature.sCPEnabled=true -Dfeature.takeovers=true"