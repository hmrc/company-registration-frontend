Company Registration Frontend
=============

[![Apache-2.0 license](http://img.shields.io/badge/license-Apache-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.html) [ ![Download](https://api.bintray.com/packages/hmrc/releases/company-registration-frontend/images/download.svg) ](https://bintray.com/hmrc/releases/company-registration-frontend/_latestVersion)


# SCRS Overview - Streamlined Company Registration Service
--------------

As part of the Small Business, Enterprise and Employment Act 2015 there is the requirement to deliver a 
system for streamlined company registration. This is to include registration in terms of :

* incorporation with Companies House as part of documents which must be delivered to the registrar under section
   9 of the Companies Act 2006 (registration documents) in respect of the formation of a company
* registration for taxes with HMRC for purposes connected with VAT, corporation tax and PAYE.

For the purposes of the legislation, streamlined means that all registration information can be provided on a 
single occasion to a single recipient and by electronic means.

Requirements 
------------

This service is written in [Scala](http://www.scala-lang.org/) and [Play](http://playframework.com/), so needs at least a [JRE] to run.

## Run the application

To run the application execute: 

```
sbt 'run 9970' 
```

and the supporting [API](https://github.com/HMRC/company-registration)

```
sbt 'run 9973' 
```

## Test the application

To test the application execute:

```
sbt test it:test
```

License
---

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").


[JRE]: http://www.oracle.com/technetwork/java/javase/overview/index.html
[API]: https://en.wikipedia.org/wiki/Application_programming_interface
[URL]: https://en.wikipedia.org/wiki/Uniform_Resource_Locator
[JSON]: http://json.org/






