# AWS Signature Version 4 test suite

The `.req` / `.creq` / `.sts` / `.authz` files are AWS's published SigV4 test vectors
(request, canonical request, string to sign, Authorization header), taken unmodified from
`tests/unit/auth/aws4_testsuite` in botocore (commit `fecd1e19cf8e1c04b66480397455e1458867bf6d`,
https://github.com/boto/botocore), which embeds the suite from
https://github.com/awslabs/aws-c-auth/tree/v0.3.15/tests/aws-sig-v4-test-suite.
The `.sreq` (signed request) files are not needed and were left out.

Copyright 2019 Amazon.com, Inc. or its affiliates. Licensed under the Apache License 2.0; the
license text is in `LICENSE` and the attribution in `NOTICE` in this directory.

Every vector shares: access key `AKIDEXAMPLE`, secret key `wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY`,
region `us-east-1`, service `service`, time `2015-08-30T12:36:00Z`. A vector's session token, when
it has one, is read from the `x-amz-security-token` line of its `.creq` (so `post-sts-header-after`,
where the token is added only after signing, signs without one). `AwsSigV4TestSuiteTest` runs all of them.
