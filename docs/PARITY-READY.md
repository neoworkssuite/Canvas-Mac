# PARITY-READY policy

`ipad-dev` is the feature-development and iPad acceptance branch. A feature may
be labelled `PARITY-READY` only when:

1. shared behavior has focused automated tests;
2. iPad simulator, physical ARM64, smoke, and applicable visual jobs pass on the
   same canonical commit;
3. platform-specific work is implemented or recorded as yellow/red in the
   parity matrix with a concrete blocker;
4. document-format changes have compatibility coverage; and
5. no user artwork, signing secret, or private repository content is committed.

Promotion to `main` requires shared validation and every applicable platform
workflow to pass. A documented yellow/red capability may remain when it is not
part of the promoted change and product status stays truthful. Source branches,
preservation tags, and original repositories are never deleted during promotion.
