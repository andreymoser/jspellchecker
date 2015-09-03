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
 * @author slickrik
 *         Error handling is insufficient.
 * @author: Andrey Chorniy
 * Date: 24.09.2008
 */


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;


public class GoogleSpellChekerServlet extends HttpServlet {

    private static final String SPELLCHECK_PROTOCOL = "https";
    private static final String SPELLCHECK_HOST = "www.google.com";
    private static final String SPELLCHECK_PATH = "/tbproxy/spell";
    private static final String[] QUERY = {"?lang=", "en", "&hl=en"};//'en' is default language

    private static final int SPELLCHECK_PORT = 443;

    public static final String[] XML = {
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>"
                    + "<spellrequest textalreadyclipped=\"0\" ignoredups=\"0\" "
                    + "ignoredigits=\"1\" ignoreallcaps=\"1\"><text>",
            "",                                                 //Place holder for the text to check
            "</text></spellrequest>"};


    public static final String[][] HEADERS = {
            {"MIME-Version", "1.0"},
            {"Content-type", "application/PTI26"},
            {"Content-transfer-encoding", "text"},
            {"Request-number", "1"},
            {"Document-type", "Request"},
            {"Interface-Version", "Test 1.4"},
            {"Connection", "close"}
    };


    private URLConnection uc;


    //Handle post/get

    public void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        PrintWriter out = response.getWriter();
        try {
            JSONObject requestJSONdata = requestDataToJSON(request.getInputStream());
            String method = requestJSONdata.getString("method");
            String language = requestJSONdata.getJSONArray("params").getString(0);
            this.connect(language);
            this.setHeaders();
            String textToCheck;

            if (method.equalsIgnoreCase("checkWords")) {
                JSONArray words = requestJSONdata.getJSONArray("params").getJSONArray(1);
                textToCheck = words.join(" ").replaceAll("\"", "");
            } else if (method.equalsIgnoreCase("getSuggestions")) {
                textToCheck = requestJSONdata.getJSONArray("params").getString(1);
            } else {
                out.println("{\"id\":null,\"results\":null,\"error\":\"Invalid request, method not yet implemented\"}");
                out.flush();
                return;
            }

            this.sendData(getXML(textToCheck));
            JSONObject responseJSON = this.receiveData(method, textToCheck);
            if (responseJSON == null) {
                out.println("{\"id\":null,\"results\":null,\"error\":\"No suggestions were received\"}");
                out.flush();
                return;
            }

            responseJSON.write(out);
            out.flush();
        } catch (JSONException e1) {
            e1.printStackTrace();
            //Send error to client, exit
            out.println("{\"id\":null,\"results\":null,\"error\":\"Invalid request, cannot parse json data\"}");
            out.flush();
            return;
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }

    }


    //Starts up the connection, use english if no language is defined

    private void connect(String language) throws IOException {
        URL url = new URL(SPELLCHECK_PROTOCOL, SPELLCHECK_HOST, SPELLCHECK_PORT,
                SPELLCHECK_PATH + QUERY[0] + ((language.compareTo("") == 0) ? QUERY[1] : language) + QUERY[2]);
        uc = url.openConnection();
    }


    //set headers for the connection

    private void setHeaders() {
        for (String[] keyValue : HEADERS) {
            uc.setRequestProperty(keyValue[0], keyValue[1]);
        }
    }


    private void sendData(String xml) throws IOException {
        uc.setDoOutput(true);
        PrintWriter pw = new PrintWriter(uc.getOutputStream());
        System.out.println("-------------------" + xml);
        pw.print(xml);
        pw.flush();
    }


    private JSONObject requestDataToJSON(InputStream requestInputStream) throws IOException, JSONException {
        //Collect data
        InputStreamReader isr = new InputStreamReader(requestInputStream);

        BufferedReader br = new BufferedReader(isr);
        String jsonString;
        StringBuilder jsonStringBuffer = new StringBuilder();

        while ((jsonString = br.readLine()) != null) {
            jsonStringBuffer.append(jsonString);
        }
        System.err.print("jsonStringBuffer" + jsonStringBuffer.toString());

        //Serialize to JSON
        return new JSONObject(jsonStringBuffer.toString());
    }


    private String getXML(String textToCheck) {
        return XML[0] + textToCheck + XML[2];
    }


    private JSONObject receiveData(String method,
                                   String textToCheck) throws IOException, ParserConfigurationException, SAXException, JSONException {

        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
        Document doc = docBuilder.parse(uc.getInputStream());

        JSONArray wordArray = new JSONArray();

        if (method.equalsIgnoreCase("checkWords")) {
            NodeList cList = doc.getElementsByTagName("c");

            for (int i = 0; i < cList.getLength(); i++) {
                Node cNode = cList.item(i);
                NamedNodeMap attrs = cNode.getAttributes();
                int offset = Integer.parseInt(attrs.getNamedItem("o").getNodeValue());//offset
                int length = Integer.parseInt(attrs.getNamedItem("l").getNodeValue());//length
                wordArray.put(textToCheck.substring(offset, offset + length));
            }

        } else if (method.equalsIgnoreCase("getSuggestions")) {

            NodeList cList = doc.getElementsByTagName("c");
            if (cList.getLength() < 1) {
                return null;//Catch and response with an error
            }

            Node cNode = cList.item(0);//Should be only one
            if (cNode.getFirstChild() == null) {
                return null;
            }

            String[] suggestions = cNode.getFirstChild().getNodeValue().split("\t");
            for (String suggestion : suggestions) {
                wordArray.put(suggestion);
            }

        }

        JSONObject returnObject = new JSONObject();
        returnObject.put("result", wordArray);
        return returnObject;
    }

}
