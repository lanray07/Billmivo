# Localisation preparation

`python tools/localise.py extract de-DE` creates a review file containing the Android strings, plural variants, planned Amazon listing copy and voice number words. The locale list distinguishes the international interface backlog from the storefront variants Amazon documents. [Amazon localisation guide](https://developer.amazon.com/docs/app-submission/distribute-your-app-internationally.html)

`python tools/localise.py translate de-DE` uses the official DeepL API with the key in `BILLMIVO_DEEPL_KEY`. The optional `BILLMIVO_DEEPL_HOST` selects the official free or pro API. The script sends only version-controlled product copy. It uses contextual terminology, protects placeholders, resumes cached entries and leaves machine translations marked for review. Check provider support for the requested target before running a batch. [DeepL translation and XML handling](https://developers.deepl.com/docs/translate/translating-xml)

Review each string in context, including plural cases, date and currency examples, and voice synonyms. Set the corresponding `reviewed` flag when reviewed, then run `python tools/localise.py render de-DE`. The script validates the current source fingerprint and placeholders before emitting Android XML, draft store metadata and voice words. Integrate those files into the Android resources, locale selection and voice dictionary in a reviewed change. On a Fire tablet, compare screen captures and test voice input separately from the interface locale.

The project currently ships English, French, Spanish and Arabic. Arabic support refers to the interface. The storefront capture set targets United Kingdom English, France French and Spain Spanish. Other regional content and device-specific speech support still require verification.
