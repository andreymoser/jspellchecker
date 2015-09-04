# jspellchecker
Java spellcheck service for tinymce 4.2.5 - forked from http://sourceforge.net/p/jspellchecker/code/HEAD/tree/branches/v1/

How to configure tinymce:
http://www.tinymce.com/wiki.php/Configuration:spellchecker_callback

Example:
```
tinymce.init({
    selector: "textarea",
    plugins: "spellchecker",
    spellchecker_languages : "+English=en-us",
    spellchecker_wordchar_pattern: /[^\s,\.]+/g,
    spellchecker_callback: function(method, text, success, failure) {
        tinymce.util.JSONRequest.sendRPC({
            url: "http://localhost:8080/jspellchecker-servlet/jazzy-spellchecker",
            method: "spellcheck",
            params: {
                lang: this.getLanguage(),
                words: text.match(this.getWordCharPattern())
            },
            success: function(result) {
                success(result);
            },
            error: function(error, xhr) {
                failure("Spellcheck error:" + xhr.status);
            }
        });
    },
    toolbar: "spellchecker"
})
```

Special thanks to Andrey Chorniy:
* https://achorniy.wordpress.com/2013/05/27/tinymce-4-spellchecker-integration
* https://achorniy.wordpress.com/2009/08/11/tinymce-spellchecker-in-java/
* http://sourceforge.net/projects/jspellchecker/

Changes summary:
* 03/09/2015 - forked from Andrey's repo (http://sourceforge.net/p/jspellchecker/code/HEAD/tree/branches/v1/)
* 03/09/2015 - added spellcheck method as required for tinymce 4.2.5 (needs to mimic JSON as example above)

