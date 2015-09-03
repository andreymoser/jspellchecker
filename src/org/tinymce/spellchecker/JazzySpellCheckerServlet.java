package org.tinymce.spellchecker;

/*
 Copyright (c) 2008
 Rich Irwin <rirwin@seacliffedu.com>, Andrey Chorniy <andrey.chorniy@gmail.com>

 Permission is hereby granted, free of charge, to any person
 obtaining a copy of this software and associated documentation
 files (the "Software"), to deal in the Software without
 restriction, including without limitation the rights to use,
 copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the
 Software is furnished to do so, subject to the following
 conditions:

 The above copyright notice and this permission notice shall be
 included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 OTHER DEALINGS IN THE SOFTWARE.
*/

/**
 * @author Rich Irwin <rirwin@seacliffedu.com>
 * @author: Andrey Chorniy <andrey.chorniy@gmail.com>
 * Date: 24.09.2008
 */

import com.swabunga.spell.engine.Configuration;
import com.swabunga.spell.engine.SpellDictionaryHashMap;
import com.swabunga.spell.engine.Word;
import com.swabunga.spell.event.SpellChecker;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

public class JazzySpellCheckerServlet extends TinyMCESpellCheckerServlet {

    private static Logger logger = Logger.getLogger(JazzySpellCheckerServlet.class.getName());
    private static final long serialVersionUID = 2L;

    private Map<String, SpellChecker> spellcheckersCache = new Hashtable<String, SpellChecker>(2);

    protected void preloadLanguageChecker(String preloadedLanguage) throws SpellCheckException {
        getChecker(preloadedLanguage);
    }

    protected List<String> findMisspelledWords(Iterator<String> checkedWordsIterator,
                                               String lang) throws SpellCheckException {
        List<String> misspelledWordsList = new ArrayList<String>();
        SpellChecker checker = (SpellChecker) getChecker(lang);
        while (checkedWordsIterator.hasNext()) {
            String word = checkedWordsIterator.next();
            if (!word.equals("") && !checker.isCorrect(word) && !checker.isCorrect(word.toLowerCase())) {
                misspelledWordsList.add(word);
            }
        }
        return misspelledWordsList;
    }

    protected List<String> findSuggestions(String word, String lang, int maxSuggestions) throws SpellCheckException {
        List<String> suggestionsList = new ArrayList<String>(maxSuggestions);
        SpellChecker checker = (SpellChecker) getChecker(lang);
        ListIterator<Word> suggestionsIt = checker.getSuggestions(word, maxSuggestions).listIterator();
        while (suggestionsIt.hasNext()) {
            suggestionsList.add(suggestionsIt.next().getWord());
        }
        return suggestionsList;
    }

    /**
     * This method look for the already created SpellChecker object in the cache, if it is not present in the cache then
     * it try to load it and put newly created object in the cache. SpellChecker loading is quite expensive operation
     * to do it for every spell-checking request, so in-memory-caching here is almost a "MUST to have"
     *
     * @param lang the language code like "en" or "en-us"
     * @return instance of jazzy SpellChecker
     * @throws SpellCheckException if method failed to load the SpellChecker for lang (it happens if there is no
     *                             dictionaries for that language was found in the classpath
     */
    protected Object getChecker(String lang) throws SpellCheckException {
        SpellChecker cachedChecker = spellcheckersCache.get(lang);
        if (cachedChecker == null) {
            cachedChecker = loadSpellChecker(lang);
            spellcheckersCache.put(lang, cachedChecker);
        }
        return cachedChecker;
    }

    /**
     * Load the SpellChecker object form the file-system.
     * TODO: It possibly worth to rework it to load from class-path, since current implementation assumes that WAR is exploded which is not always can be the case
     *
     * @param lang
     * @return loaded SpellChecker object
     * @throws SpellCheckException
     */
    private SpellChecker loadSpellChecker(final String lang) throws SpellCheckException {
        SpellChecker checker = new SpellChecker();
        List<File> dictionariesFiles = getDictionaryFiles(lang);

        addDictionaries(checker, dictionariesFiles);
        configureSpellChecker(checker);

        return checker;
    }

    /**
     * @param language
     * @return List of spellcheckers dictionary files in the webapplicaiton /WEB-INF/dictionary/${language} for the language
     * @throws SpellCheckException if there is no dictionaries for the specifed language
     */

    protected List<File> getDictionaryFiles(String language) throws SpellCheckException {
        String pathToDictionaries = getServletContext().getRealPath("/WEB-INF/dictionary/jazzy");
        File dictionariesDir = new File(pathToDictionaries);
        List<File> langDictionaries = getDictionaryFiles(language, dictionariesDir, language);
        if (langDictionaries.size() == 0) {
            throw new SpellCheckException("There is no dictionaries for the language=" + language);
        }
        List<File> globalDictionaries = getDictionaryFiles(language, dictionariesDir, "global");

        List<File> dictionariesFiles = new ArrayList<File>();
        dictionariesFiles.addAll(langDictionaries);
        dictionariesFiles.addAll(globalDictionaries);
        return dictionariesFiles;
    }

    private void configureSpellChecker(SpellChecker checker) {
        checker.getConfiguration().setBoolean(Configuration.SPELL_IGNOREDIGITWORDS, false);
        checker.getConfiguration().setBoolean(Configuration.SPELL_IGNOREINTERNETADDRESSES, true);
        checker.getConfiguration().setBoolean(Configuration.SPELL_IGNOREMIXEDCASE, true);
        checker.getConfiguration().setBoolean(Configuration.SPELL_IGNOREUPPERCASE, true);
        checker.getConfiguration().setBoolean(Configuration.SPELL_IGNORESENTENCECAPITALIZATION, false);
    }

    private void addDictionaries(SpellChecker checker, List<File> langDictionaries) throws SpellCheckException {
        for (File dicitonaryFile : langDictionaries) {
            Reader dictionaryReader = null;
            try {
                dictionaryReader = new BufferedReader(new InputStreamReader(new FileInputStream(dicitonaryFile)));
                checker.addDictionary(new SpellDictionaryHashMap(dictionaryReader));
            } catch (Exception ex) {
                throw new SpellCheckException("Failed to open dictionary=" + dicitonaryFile.getName(), ex);
            } finally {
                if (dictionaryReader != null) {
                    try {
                        dictionaryReader.close();
                    } catch (IOException e) {
                        logger.warning("Failed to close dictionary file " + dicitonaryFile.getPath());
                    }
                }
            }
        }
    }


    private List<File> getDictionaryFiles(final String lang, File dictionariesDir,
                                          final String prefix) throws SpellCheckException {
        File languageDictionary = new File(dictionariesDir, lang);
        File[] languageDictionaries = languageDictionary.listFiles(new FileFilter() {
            public boolean accept(File pathName) {
                if (pathName.isFile()) {
                    return pathName.getName().startsWith(prefix);
                }
                return false;
            }
        });

        List<File> dicitonaries = new ArrayList<File>();
        if (languageDictionaries != null) {
            dicitonaries.addAll(Arrays.asList(languageDictionaries));
        }
        return dicitonaries;
    }

    @Override
    protected void clearSpellcheckerCache() {
        spellcheckersCache.clear();
        spellcheckersCache = new Hashtable<String, SpellChecker>();
    }
}