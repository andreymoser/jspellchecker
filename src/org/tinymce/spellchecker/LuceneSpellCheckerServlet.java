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
 * @author: Andrey Chorniy <andrey.chorniy@gmail.com>
 * Date: 11.05.2010
 */

import org.apache.lucene.search.spell.PlainTextDictionary;
import org.apache.lucene.search.spell.SpellChecker;
import org.apache.lucene.search.spell.StringDistance;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;

import javax.servlet.ServletException;
import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LuceneSpellCheckerServlet extends TinyMCESpellCheckerServlet {

    private static final String MAX_MEMORY_USAGE_PARAM = "max_memory_usage";
    private long maxMemorytUsage = 128*1024*1024; //128 megabytes
    

    private static Logger logger = Logger.getLogger(LuceneSpellCheckerServlet.class.getName());

    private Map<String, MemoryAwareSpellChecker> spellcheckersCache = new Hashtable<String, MemoryAwareSpellChecker>();

    private Map<String, MemoryAwareSpellChecker> inMemorySpellcheckersCache = new LinkedHashMap<String, MemoryAwareSpellChecker>(2, 0.75f, true){
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, MemoryAwareSpellChecker> eldest) {
            return getSizeInBytes() > maxMemorytUsage;
        }

        public long getSizeInBytes(){
            long size = 0;
            for (MemoryAwareSpellChecker spellChecker : values()) {
                size += spellChecker.getIndexSize();
            }
            return size;
        }
    };
    private static final String WEB_INF_DICTIONARY_LUCENE_DICTIONARIES = "/WEB-INF/dictionary/lucene";

    @Override
    public void init() throws ServletException {
        super.init();
        String memoryUsageParam = getServletConfig().getInitParameter(MAX_MEMORY_USAGE_PARAM);
        if (memoryUsageParam != null && memoryUsageParam.trim().length() > 0) {
            try {
                maxMemorytUsage = Long.parseLong(memoryUsageParam.trim())*1024*1024;
            } catch (NumberFormatException ex) {
                //wrong servlet configuration, possibly a typo
                throw new ServletException(ex);
            }
        }
    }

    private Set<String> indexedLanguages = new HashSet<String>();

    protected void preloadLanguageChecker(String preloadedLanguage) throws SpellCheckException {
        getChecker(preloadedLanguage);
    }

    protected List<String> findMisspelledWords(Iterator<String> checkedWordsIterator,
                                               String lang) throws SpellCheckException {
        List<String> misspelledWordsList = new ArrayList<String>();
        SpellChecker checker = (SpellChecker) getChecker(lang);
        try {
            while (checkedWordsIterator.hasNext()) {
                String word = checkedWordsIterator.next();
                if (!word.equals("") && !checker.exist(word)) {
                    misspelledWordsList.add(word);
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to find misspelled words", e);
            throw new SpellCheckException("Failed to find misspelled words", e);
        }
        return misspelledWordsList;
    }

    protected List<String> findSuggestions(String word, String lang, int maxSuggestions) throws SpellCheckException {
        SpellChecker checker = (SpellChecker) getChecker(lang);
        try {
            String[] suggestions = checker.suggestSimilar(word, maxSuggestions);
            return Arrays.asList(suggestions);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to find suggestions", e);
            throw new SpellCheckException("Failed to find suggestions", e);
        }
    }

    /**
     * This method use 2-level cache to achieve the maximum performance and memory-management.
     * 1-st level of the cache is the cache of SpellCheckers which use In-Memory (RAMDirectory) Lucene indexes<br/>
     * 2-nd level cache store File-System SpellCheckers (FSDirectory) which don't take memory but just hold the reference to the Directory object<br/>
     * 1-st level cache implementation (LinkedHashMap) is also responsible for memory-management, it guarantees that summary
     * size of all In-Memory indexes is less than maxMemoryUsage (in bytes)
     *
     * @param lang the language code like "en" or "en-us"
     * @return instance of Lucene SpellChecker
     * @throws org.tinymce.spellchecker.SpellCheckException
     *          if method failed to load the SpellChecker for lang (it happens if there is no
     *          dictionaries for that language was found in the classpath
     */
    protected Object getChecker(String lang) throws SpellCheckException {
        MemoryAwareSpellChecker cachedChecker = inMemorySpellcheckersCache.get(lang);
        if (cachedChecker == null){
            MemoryAwareSpellChecker diskSpellChecker = spellcheckersCache.get(lang);
            if (diskSpellChecker == null){
                diskSpellChecker = loadSpellChecker(lang);
                spellcheckersCache.put(lang, diskSpellChecker);
            }
            
            try {
                cachedChecker = new MemoryAwareSpellChecker(new RAMDirectory(diskSpellChecker.getSpellIndex()));
                inMemorySpellcheckersCache.put(lang, cachedChecker);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to read index", e);
                throw new SpellCheckException("Failed to read index", e);
            }
        }

        return cachedChecker;
    }

    /**
     * Load the SpellChecker object form the file-system.
     * @param lang
     * @return loaded SpellChecker object
     * @throws org.tinymce.spellchecker.SpellCheckException
     *
     */
    private MemoryAwareSpellChecker loadSpellChecker(final String lang) throws SpellCheckException {
        MemoryAwareSpellChecker checker = null;
        if (!indexedLanguages.contains(lang)){
            indexedLanguages.add(lang);
            checker = reindexSpellchecker(lang);
        } else {
            try {
                checker = new MemoryAwareSpellChecker(getSpellCheckerDirectory(lang));
            } catch (IOException e) {
                throw new SpellCheckException("Failed to create index",e);
            }
        }
        return checker;
    }

    private MemoryAwareSpellChecker reindexSpellchecker(String lang) throws SpellCheckException {
        MemoryAwareSpellChecker checker;
        List<File> dictionariesFiles = getDictionaryFiles(lang);
        try {
            checker = new MemoryAwareSpellChecker(getSpellCheckerDirectory(lang));
            checker.clearIndex();
        } catch (IOException e) {
            throw new SpellCheckException("Failed to create index",e);
        }

        for (File dictionariesFile : dictionariesFiles) {
            try {
                checker.indexDictionary(new PlainTextDictionary(dictionariesFile));
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to index dictionary "+dictionariesFile.getAbsolutePath(), e);

                throw new SpellCheckException("Failed to index dictionary "+dictionariesFile.getAbsolutePath(), e);
            }
        }
        return checker;
    }

    /**
     * @param language the language to load dictionaries for
     * @return List of spellcheckers dictionary files in the web-applicaiton /WEB-INF/dictionary/${language} for the language
     * @throws org.tinymce.spellchecker.SpellCheckException
     *          if there is no dictionaries for the specified language
     */

    protected List<File> getDictionaryFiles(String language) throws SpellCheckException {
        String pathToDictionaries = getServletContext().getRealPath(WEB_INF_DICTIONARY_LUCENE_DICTIONARIES);
        File dictionariesDir = new File(pathToDictionaries);
        List<File> langDictionaries = getDictionaryFiles(language, dictionariesDir, language);
        if (langDictionaries.size() == 0) {
            throw new SpellCheckException("There is no dictionaries for the language=" + language);
        }
        List<File> globalDictionaries = getDictionaryFiles("global", dictionariesDir, "global");

        List<File> dictionariesFiles = new ArrayList<File>();
        dictionariesFiles.addAll(langDictionaries);
        dictionariesFiles.addAll(globalDictionaries);
        return dictionariesFiles;
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

        List<File> dictionaries = new ArrayList<File>();
        if (languageDictionaries != null) {
            dictionaries.addAll(Arrays.asList(languageDictionaries));
        }
        return dictionaries;
    }

    @Override
    protected void clearSpellcheckerCache() {
        spellcheckersCache.clear();
        spellcheckersCache = new Hashtable<String, MemoryAwareSpellChecker>();
    }

    /**
     * @param language
     * @return the Lucene Directory object for indexedClass and Entity. it is constructed as
     * "${base-spellchecker-directory}/${indexed-class-name}/${indexedField}" so each field indexes are stored in it's
     * own file-directory inside owning-class directory
     * @throws IOException
     */
    private Directory getSpellCheckerDirectory(String language) throws IOException {
        String path = "./spellchecker/lucene/" + language;
        return FSDirectory.getDirectory(path);
    }

    private class MemoryAwareSpellChecker extends SpellChecker{
        Directory _spellIndex;

        private MemoryAwareSpellChecker(Directory spellIndex) throws IOException {
            super(spellIndex);
            _spellIndex = spellIndex;
        }

        private MemoryAwareSpellChecker(Directory spellIndex,
                               StringDistance sd) throws IOException {
            super(spellIndex, sd);
            _spellIndex = spellIndex;
        }

        long getIndexSize(){
            if (_spellIndex instanceof RAMDirectory){
                return ((RAMDirectory) _spellIndex).sizeInBytes();
            }
            return 0;
        }

        Directory getSpellIndex(){
            return _spellIndex;
        }
    }
}