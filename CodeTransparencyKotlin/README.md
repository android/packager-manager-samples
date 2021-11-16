# Code Transparency Verification Sample
The purpose of this sample is to show how to perform code transparency check.
Code transparency is an optional code signing and verification mechanism. Code
transparency verification is used solely for the purpose of inspection by
developers and end users, who want to ensure that code they're running matches
the code that was originally built and signed by the app developer. Code
transparency is independent of the signing scheme used for app bundles and APKs.

The process works by including a code transparency file in the bundle after it
has been built, but before it is uploaded to Play Console for distribution.

The code transparency file is a JSON Web Token (JWT) that contains a list of DEX
files and native libraries included in the bundle, and their hashes. It is then
signed using the code transparency key that is held only by the developer.

Together, this information verifies that the code contained in the APKs matches
what the developer had intended, and that it has not been modified.

## The process
* Load the code transparency file and deserialize JsonWebSignature.
* Verify integrity of DEX files and native libraries. For each of the files:
- Compute file hash
- Check if the hash is present in the JWT

## Additional resources
* [Code transparency documentation](https://developer.android.com/guide/app-bundle/code-transparency)