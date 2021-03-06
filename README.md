# jspellchecker
Java spellcheck service for tinymce 4.2.5 - forked from http://sourceforge.net/p/jspellchecker/code/HEAD/tree/branches/v1/

Requirements:
* App servers compatible with Java 8 (see notes below)

Download:
* https://github.com/andreymoser/jspellchecker/tree/master/releases/latest

Tinymce spellchecker configuration:
* http://www.tinymce.com/wiki.php/Configuration:spellchecker_callback
* http://www.tinymce.com/wiki.php/Plugin:spellchecker

HTTP post example:
```
tinymce.init({
    selector: "textarea",
    plugins: "spellchecker",
    spellchecker_languages : "+English=en-us",
    spellchecker_rpc_url: "http://localhost:8080/jspellchecker/jazzy-spellchecker",
    toolbar: "spellchecker"
})
```

AJAX example:
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

Changes summary:
* 03/09/2015 - forked from Andrey's repo (http://sourceforge.net/p/jspellchecker/code/HEAD/tree/branches/v1/)
* 03/09/2015 - added spellcheck method as required for tinymce 4.2.5 (needs to mimic JSON as example above using AJAX wrapper)
* 04/09/2015 - published war to download and added requirements
* 10/09/2015 - added support for application/x-www-form-urlencoded content-type (tinymce 4.2.x HTTP post)

Application servers tested:
* Apache tomcat 7

About the dictionaries:
* Dictionaries aren't included. You'll need to configure them. I recommend to read Chorniy's blog mentioned below.

Notes:
* In case your app server is incompatible you can build the deployable war file via gradle. It depends on current dependencies and it haven't been analyzed and tested. Basically you need to clone this repository form git, then execute 'gradle war' using the desired JDK version.
 
Special thanks to Andrey Chorniy:
* https://achorniy.wordpress.com/2013/05/27/tinymce-4-spellchecker-integration
* https://achorniy.wordpress.com/2009/08/11/tinymce-spellchecker-in-java
* http://sourceforge.net/projects/jspellchecker