/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
/**
 * Converts the javadoc comment in the {@code package-info.java} file into Oracle Wiki format.
 */
package com.oracle.max.tools.javadoc.wiki;

import java.io.*;
import java.util.*;

import com.sun.max.ide.JavaProject;
import com.sun.max.program.Classpath;
import com.sun.max.program.ClasspathTraversal;
import com.sun.javadoc.*;

/**
 * {@link Doclet} to convert package-info javadoc to Oracle Wiki format.
 *
 * The conversion includes inline tags and embedded HTML.
 * There are many heuristics used to do a reasonable, given the ad hoc nature of HTML
 * and the Wiki notation rules. Writing proper HTML, e.g., always including end tags, helps.
 */
public class WikiDoclet extends Doclet {
    private static final String TEXT = "Text";
    private static final String KENAI_MAXINE_TIP = "http://kenai.com/hg/maxine~maxine/file/tip";
    private static final String HIDDEN_EXCERPT_START = "{excerpt:hidden=true}";
    private static final String EXCERPT_END = "{excerpt}\n";
    private static final String AUTO_GEN_BEGIN = HIDDEN_EXCERPT_START + "DO NOT EDIT: Automatically generated from ";
    private static final String AUTO_GEN_END = EXCERPT_END;
    private static final String HRULE = "\n----\n";
    private static String outputDir;
    private static String docletPath;
    private static String projectList;
    private static boolean includeToc = true;
    private static StringBuilder sb;
    private static Map<String, Integer> headerDepth = new HashMap<String, Integer>();
    private static Map<String, String> validHtmlTags = new HashMap<String, String>();
    private static Map<String, String> classToProject = new HashMap<String, String>();
    private static char[] commentTextArray;
    private static Stack<HtmlTag> lists = new Stack<HtmlTag>();

    static class WikiException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        WikiException(String message) {
            super(message);
        }
    }

    /**
     * Records information on an HTML tag, specifically the tag name and any embedded attributes.
     */
    private static class HtmlTag {
        String tag;
        String attributes;

        HtmlTag(String tag, String attributes) {
            this.tag = tag;
            this.attributes = attributes;
        }

        static HtmlTag createTag(StringRange range) {
            int spaceIndex = range.string.indexOf(' ', range.startIndex);
            if (spaceIndex >= range.endIndex) {
                spaceIndex = -1;
            }
            String tag = range.string.substring(range.startIndex, spaceIndex < 0 ? range.endIndex : spaceIndex).toUpperCase();
            String attributes = null;
            if (spaceIndex >= 0) {
                attributes = range.string.substring(spaceIndex + 1, range.endIndex);
            }
            return new HtmlTag(tag, attributes);
        }

        @Override
        public String toString() {
            return "<" + tag + (attributes != null ? " " + attributes : "") + ">";
        }
    }

    /**
     * Denotes a subrange of a string and provides search operations that are constrained to the range.
     */
    private static class StringRange {
        final String string;
        String substring; // lazily created
        final int startIndex;
        final int endIndex;

        StringRange(String string, int startIndex, int endIndex) {
            this.string = string;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            assert endIndex >= startIndex;
        }

        StringRange(StringRange range, int startIndex, int endIndex) {
            this(range.string, startIndex, endIndex);
        }

        private String makeSubstring() {
            if (substring == null) {
                substring = string.substring(startIndex, endIndex);
            }
            return substring;
        }

        String content() {
            return makeSubstring();
        }

        /*
         * Searches that use String.indexOf but handles the range constraints.
         * The fromIndex argument indexes "string".
         */

        int indexOf(char ch, int fromIndex) {
            if (fromIndex >= endIndex) {
                return -1;
            }
            int result = makeSubstring().indexOf(ch, fromIndex - startIndex);
            return result < 0 ? result : result + startIndex;
        }

        int indexOf(String s, int fromIndex) {
            if (fromIndex >= endIndex) {
                return -1;
            }
            int result = makeSubstring().indexOf(s, fromIndex - startIndex);
            return result < 0 ? result : result + startIndex;
        }

        int indexOf(char ch) {
            return indexOf(ch, startIndex);
        }

        @Override
        public String toString() {
            return "\"" + makeSubstring() + "\"\nstart: " + startIndex + ", end: " + endIndex;
        }
    }

    /**
     * Denotes the the content between an HTML tag and its matching end tag, with
     * the index of the character after the '>' of the end tag for convenience.
     */
    private static class HtmlTagData extends StringRange {
        int tagEndIndex;

        HtmlTagData(StringRange range, int startIndex, int endIndex, int tagEndIndex) {
            super(range, startIndex, endIndex);
            this.tagEndIndex = tagEndIndex;
        }

        @Override
        public String toString() {
            return super.toString() + ", tagEndIndex: " + tagEndIndex;
        }
    }

    private static class InlineTagInfo {
        Tag inlineTag;
        StringRange range;
        InlineTagInfo(Tag inlineTag, StringRange range) {
            this.inlineTag = inlineTag;
            this.range = range;
        }

        @Override
        public String toString() {
            return Tag.class.getName() + ", index: " + range;
        }
    }

    /**
     * An entity is either an HTML tag or an inline javadoc tag.
     * The {@code index} value is the start of the entity.
     * In the case of HTML, it is the index of the {@code '<'} character.
     */
    private static abstract class EntityIndex {
        final int index;

        EntityIndex(int index) {
            this.index = index;
        }

    }

    private static class InlineTagEntityIndex extends EntityIndex {
        /**
         * The {@link InlineTagInfo} associated with this javadoc inline tag.
         */
        InlineTagInfo inlineTagInfo;

        InlineTagEntityIndex(int index, InlineTagInfo inlineTagInfo) {
            super(index);
            assert inlineTagInfo != null;
            this.inlineTagInfo = inlineTagInfo;
        }
    }

    private static class HtmlTagEntityIndex extends EntityIndex {
        HtmlTagEntityIndex(int index) {
            super(index);
        }
    }

    public static boolean start(RootDoc root) {
        readOptions(root.options());
        addHtmlTags();
        buildClassToProjectsMap();
        new File(outputDir).mkdir();

        PackageDoc[] packageDocs = root.specifiedPackages();
        for (PackageDoc packageDoc : packageDocs) {
            try {
                Tag[] inlineTags = packageDoc.inlineTags();
                String commentText = packageDoc.commentText();
                processPackageInfoDoc(packageDoc, commentText, inlineTags);
            } catch (Exception ex) {
                System.err.println("Exception processing package " + packageDoc.name() + ": " + ex);
                System.exit(1);
            }
        }
        return true;
    }

    private static void buildClassToProjectsMap() {
        // We can't use system classpath to find workspace root as it doesn't include any Maxine projects
        // javadoc -classpath is NOT the same as java -classpath!
        final File wsRoot = JavaProject.findWorkspace(new Classpath(docletPath));
        final int wsRootLength = wsRoot.getAbsolutePath().length();
        ArrayList<Classpath.Entry> projectEntries = new ArrayList<Classpath.Entry>();
        String[] projects = projectList.split(",");
        for (String project : projects) {
            projectEntries.add(new Classpath.Directory(new File(new File(wsRoot, project), "bin")));
        }
        Classpath projectClasspath = new Classpath(projectEntries);
        new ClasspathTraversal() {

            @Override
            protected boolean visitFile(File parent, String resource) {
                if (resource.endsWith(".class")) {
                    int x = resource.lastIndexOf(".class");
                    classToProject.put(resource.substring(0, x).replace('/', '.').replace('$', '.'), getProject(wsRootLength, parent.getAbsolutePath()));
                }
                return true;
            }
        }.run(projectClasspath);
    }

    private static String getProject(int wsRootEndIndex, String binPath) {
        int binIndex = binPath.lastIndexOf("/bin");
        return binPath.substring(wsRootEndIndex + 1, binIndex);
    }

    private static void addHtmlTags() {
        for (int i = 1; i <= 4; i++) {
            String hd = "H" + i;
            headerDepth.put(hd, i);
            addHtmlTag(hd);
        }
        addHtmlTag("P");
        addHtmlTag("I");
        addHtmlTag("UL");
        addHtmlTag("OL");
        addHtmlTag("I");
        addHtmlTag("B");
        addHtmlTag("LI");
        addHtmlTag("PRE");
        addHtmlTag("A");
        addHtmlTag("HR");
        addHtmlTag("BR");
    }

    private static void addHtmlTag(String h) {
        validHtmlTags.put(h, h);
    }

    private static void processPackageInfoDoc(PackageDoc packageDoc, String commentText, Tag[] inlineTags) {
        commentTextArray = new char[commentText.length()];
        for (int i = 0; i < commentText.length(); i++) {
            commentTextArray[i] = commentText.charAt(i);
        }
        InlineTagInfo[] inlineTagInfo = computeTagIndices(commentText, inlineTags);
        String packageInfo = "{{" + packageDoc.name() + ".package-info}}";
        sb = new StringBuilder();
        sb.append(AUTO_GEN_BEGIN);
        sb.append(packageInfo);
        sb.append(AUTO_GEN_END);
        if (includeToc) {
            sb.append("{toc}\n");
        }
        processText(new StringRange(commentText, 0, commentText.length()), inlineTagInfo, null);
        sb.append(HRULE);
        sb.append("Automatically generated from ");
        sb.append(packageInfo);
        File wikiFile = new File(outputDir, packageDoc.name() + ".wiki");
        BufferedWriter wr = null;
        try {
            wr = new BufferedWriter(new FileWriter(wikiFile));
            wr.write(sb.toString());
        } catch (IOException ex) {
            System.err.println(ex);
        } finally {
            if (wr != null) {
                try {
                    wr.close();
                } catch (IOException ex) {
                }
            }
        }
    }

    /**
     * Compute the actual indices of the non-text tags in the comment text.
     * Experimentally, every tag in, say, a package-info comment, has the same "position",
     * that of the package statement - not very helpful.
     * @param inlineTags
     * @return
     */
    private static InlineTagInfo[] computeTagIndices(String commentText, Tag[] inlineTags) {
        ArrayList<InlineTagInfo> indicesList = new ArrayList<InlineTagInfo>();
        int index = 0;
        for (int i = 0; i < inlineTags.length; i++) {
            Tag tag = inlineTags[i];
            String tagText = tag.text();
            String tagName = tag.name();
            if (tagName.equals(TEXT)) {
                index += tagText.length();
            } else {
                int startIndex = index;
                assert commentText.charAt(index) == '{';
                int x = commentText.indexOf(tagText, index);
                assert x >= 0;
                x = commentText.indexOf('}', x + tagText.length());
                assert x >= 0;
                index = x + 1;
                indicesList.add(new InlineTagInfo(tag, new StringRange(commentText, startIndex, index)));
            }
        }
        InlineTagInfo[] indices = new InlineTagInfo[indicesList.size()];
        indicesList.toArray(indices);
        return indices;
    }

    /**
     * Convert any embedded HTML tags and inline javadoc tags in {@code range} into Wiki equivalents.
     * HTML tag content can contain inline tags, but not vice versa.
     * Transformed text is appended to {@link #sb}.
     * @param range {@code StringRange} to process
     * @param inlineTagInfo info on where the inline tags are
     * @param lastEntityIndex of the last entity processed
     *
     */
    private static void processText(StringRange range, InlineTagInfo[] inlineTagInfo, EntityIndex lastEntityIndex) {
        int lastIndex = range.startIndex;
        while (lastIndex < range.endIndex) {
            // Create a new subrange starting at lastIndex
            StringRange subRange = new StringRange(range, lastIndex, range.endIndex);
            // Find the next entity in newRange
            EntityIndex entityIndex = nextEntity(subRange, inlineTagInfo);
            if (entityIndex == null) {
                // no more, copy remaining content and terminate loop
                sb.append(fixLineBreaks(subRange, lastEntityIndex));
                break;
            }
            // copy content from lastIndex to start of new entity
            sb.append(fixLineBreaks(new StringRange(range, lastIndex, entityIndex.index), lastEntityIndex));
            // save entityIndex for next iteration
            lastEntityIndex = entityIndex;
            if (entityIndex instanceof InlineTagEntityIndex) {
                // handle inline javadoc tag
                InlineTagEntityIndex inlineTagEntityIndex = (InlineTagEntityIndex) entityIndex;
                Tag inlineTag = inlineTagEntityIndex.inlineTagInfo.inlineTag;
                if (inlineTag.name().equals("@code")) {
                    sb.append("{{");
                    sb.append(fixWikiEscapes(fixEntityReferences(inlineTag.text())));
                    sb.append("}}");
                } else if (inlineTag.name().startsWith("@link")) {
                    SeeTag seeTag = (SeeTag) inlineTag;
                    boolean plain = inlineTag.name().equals("@linkplain");
                    String label = seeTag.label();
                    if (label.isEmpty()) {
                        label = null;
                    }
                    String className = seeTag.referencedClassName();
                    String simpleClassName = stripPackage(className);
                    String projectName = classToProject.get(className);
                    String memberName = seeTag.referencedMemberName();
                    String classAndMemberName = memberName == null ? simpleClassName : simpleClassName + "." + memberName;
                    if (projectName != null) {
                        sb.append('[');
                    }
                    if (!plain) {
                        sb.append("{{");
                    }
                    sb.append(label == null ? classAndMemberName : label);
                    if (!plain) {
                        sb.append("}}");
                    }
                    if (projectName != null) {
                        // link to source on kenai
                        sb.append(createKenaiPath(projectName, className));
                    }

                } else {
                    assert false;
                }
                // step lastIndex beyond inline tag
                lastIndex = inlineTagEntityIndex.inlineTagInfo.range.endIndex;
            } else if (entityIndex instanceof HtmlTagEntityIndex) {
                // process an HTML tag
                int tagEndIndex = subRange.indexOf('>', entityIndex.index);
                // check tag is well formed
                if (tagEndIndex < 0) {
                    throw new WikiException("malformed HTML tag");
                }
                // create a new HTML tag from the tag content minus the <>
                HtmlTag htmlTagInfo = HtmlTag.createTag(new StringRange(subRange.string, entityIndex.index + 1, tagEndIndex));
                String htmlTag = htmlTagInfo.tag;
                // a tag we don't handle (yet)
                if (validHtmlTags.get(htmlTag) == null) {
                    throw new WikiException("unimplemented HTML tag: " + htmlTag);
                }
                // HTML is horribly irregular but in "good" HTML, many tags have matching end tags
                // and we want to process the internal context recursively.
                HtmlTagData data = findmatchingTag(subRange, htmlTag, tagEndIndex + 1);
                int hd = isHeader(htmlTag);
                if (hd > 0) {
                    sb.append('\n');
                    sb.append('h');
                    sb.append(hd);
                    sb.append(". ");
                    processText(data, inlineTagInfo, lastEntityIndex);
                    sb.append('\n');
                } else if (htmlTag.equals("I")) {
                    sb.append('_');
                    sb.append(data.content());
                    sb.append('_');
                } else if (htmlTag.equals("B")) {
                    sb.append('*');
                    sb.append(data.content());
                    sb.append('*');
                } else if (htmlTag.equals("P")) {
                    // matching tag usually omitted
                    sb.append('\n');
                    if (data != null) {
                        processText(data, inlineTagInfo, lastEntityIndex);
                    } else {
                        lastIndex = tagEndIndex + 1;
                    }
                    sb.append('\n');
                } else if (htmlTag.equals("UL") | htmlTag.equals("OL")) {
                    lists.push(htmlTagInfo);
                    processText(data, inlineTagInfo, lastEntityIndex);
                    lists.pop();
                    sb.append('\n');
                } else if (htmlTag.equals("PRE")) {
                    // no interpretation of body
                    sb.append("{code}\n");
                    sb.append(replacePreLeadingSpaces(fixEntityReferences(data.content())));
                    sb.append("{code}\n");
                } else if (htmlTag.equals("LI")) {
                    sb.append('\n');
                    processLists();
                    processText(data, inlineTagInfo, lastEntityIndex);
                } else if (htmlTag.equals("A")) {
                    sb.append('[');
                    sb.append(data.content());
                    sb.append('|');
                    sb.append(getHRef(htmlTagInfo.attributes));
                    sb.append(']');
                } else if (htmlTag.equals("HR")) {
                    sb.append(HRULE);
                } else if (htmlTag.equals("BR")) {
                    sb.append("\n");
                }
                if (data != null) {
                    lastIndex = data.tagEndIndex;
                } else {
                    lastIndex = tagEndIndex + 1;
                }
            }
        }
    }

    private static void processLists() {
        for (HtmlTag htmlTag : lists) {
            if (htmlTag.tag.equals("UL")) {
                sb.append('*');
            } else if (htmlTag.tag.equals("OL")) {
                sb.append('#');
            } else {
                assert false;
            }
        }
        sb.append(' ');
    }

    private static String getHRef(String s) {
        int index = s.indexOf('"');
        int lastIndex = s.lastIndexOf('"');
        return s.substring(index + 1, lastIndex);
    }

    private static String createKenaiPath(String projectName, String className) {
        return createKenaiPath(projectName, className, true);
    }

    private static String createKenaiPath(String projectName, String className, boolean inLink) {
        StringBuilder ssb = new StringBuilder();
        if (inLink) {
            ssb.append('|');
        }
        ssb.append(KENAI_MAXINE_TIP);
        ssb.append('/');
        ssb.append(projectName);
        ssb.append("/src/");
        ssb.append(className.replace('.', '/'));
        ssb.append(".java");
        if (inLink) {
            ssb.append(']');
        }
        return ssb.toString();
    }

    /**
     * In package-info files class names must be fully qualified (pain), but javadoc
     * strips the package in the HTML, and we do the same.
     * @param qualName
     */
    private static String stripPackage(String qualName) {
        for (int i = 0; i < qualName.length(); i++) {
            if (Character.isUpperCase(qualName.charAt(i))) {
                return i == 0 ? qualName : qualName.substring(i);
            }
        }
        return qualName;
    }

    /**
     * Remove internal line breaks and leading space from the javadoc comment as Wiki will treat them literally.
     * Very heuristic unfortunately.
     * @param range string range to be analyzed
     * @return
     */
    private static String fixLineBreaks(StringRange range, EntityIndex lastEntityIndex) {
        String result = "";
        int index = range.startIndex;
        while (index < range.endIndex) {
            int breakIndex = range.indexOf("\n ", index);
            if (breakIndex < 0 || breakIndex >= range.endIndex - 2) {
                if (breakIndex == range.endIndex - 2) {
                    // if it ends in "\n " we drop the newline but keep the space
                    result += range.string.substring(index, range.endIndex - 2) + " ";
                } else {
                    result += range.string.substring(index, range.endIndex);
                }
                break;
            }
            result += range.string.substring(index, breakIndex);
            // if at start, after HTML, skip newline and space, else just the newline (space becomes separator)
            if (breakIndex == range.startIndex && (lastEntityIndex != null && lastEntityIndex instanceof HtmlTagEntityIndex)) {
                index = breakIndex + 2;
            } else {
                index = breakIndex + 1;
            }
        }
        return result;
    }

    private static String fixEntityReferences(String s) {
        StringBuilder ssb = new StringBuilder();
        int index = 0;
        while (index < s.length()) {
            int eIndex = s.indexOf('&', index);
            if (eIndex < 0) {
                break;
            }
               // append up to the '&'
            ssb.append(s.substring(index, eIndex));
            eIndex++;
            char c = s.charAt(eIndex);
            if (c == '#') {
                int nIndex = eIndex + 1;
                char dig = s.charAt(nIndex);
                int code = 0;
                while (dig >= '0' && dig <= '9') {
                    code = code * 10 + (dig - '0');
                    nIndex++;
                    dig = s.charAt(nIndex);
                }
                ssb.append((char) code);
                index = nIndex;
            } else if (entityMatch(s, eIndex, "amp")) {
                ssb.append('&');
                index = eIndex + 4;
            } else if (entityMatch(s, eIndex, "gt")) {
                ssb.append('>');
                index = eIndex + 3;
            } else if (entityMatch(s, eIndex, "ge")) {
                ssb.append(">=");
                index = eIndex + 3;
            } else if (entityMatch(s, eIndex, "lt")) {
                ssb.append('<');
                index = eIndex + 3;
            } else if (entityMatch(s, eIndex, "le")) {
                ssb.append("<=");
                index = eIndex + 3;
            } else {
                throw new WikiException("undecoded character entity reference");
            }
        }
        ssb.append(s, index, s.length());
        return ssb.toString();
    }

    private static boolean entityMatch(String s, int index, String m) {
        try {
            for (int i = 0; i < m.length(); i++) {
                if (s.charAt(index + i) != m.charAt(i)) {
                    return false;
                }
            }
            return s.charAt(index + m.length()) == ';';
        } catch (StringIndexOutOfBoundsException ex) {
            return false;
        }
    }

    private static String fixWikiEscapes(String s) {
        StringBuilder ssb = new StringBuilder();
        int index = 0;
        while (index < s.length()) {
            char ch = s.charAt(index);
            if (ch == '[' || ch == ']') {
                ssb.append('\\');
            }
            ssb.append(ch);
            index++;
        }
        return ssb.toString();
    }

    /**
     * Remove the first leading space after a newline (arises due to the way javadoc comments are written).
     * @param s
     * @return
     */
    private static String replacePreLeadingSpaces(String s) {
        StringBuilder ssb = new StringBuilder();
        int index = 0;
        while (index < s.length()) {
            int nlIndex = s.indexOf("\n ", index);
            if (nlIndex < 0) {
                break;
            }
            // There is always a "\n " at the start that we just want to ignore
            if (index > 0) {
                // append up to and including the newline
                nlIndex++;
                ssb.append(s.substring(index, nlIndex));
                // check for leading space
                try {
                    if (s.charAt(nlIndex) == ' ') {
                        nlIndex++;
                    }
                } catch (StringIndexOutOfBoundsException ex) {
                    break;
                }
            } else {
                nlIndex = 2;
            }
            index = nlIndex;
        }
        // append everything after the last position
        ssb.append(s.substring(index));
        return ssb.toString();
    }

    /**
     * Finds the index of the next inline tag or HTML tag in {@code s[range]}.
     * @param range string range to search
     * @param inlineTagInfo
     * @return an {@link EntityIndex} or null if not found
     */
    private static EntityIndex nextEntity(StringRange range, InlineTagInfo[] inlineTagInfo) {
        int htmlTagIndex = range.indexOf('<');
        if (htmlTagIndex < 0 || (range.string.charAt(htmlTagIndex + 1) == '/')) {
            htmlTagIndex = -1;
        }
        int inlineTagIndex = -1;
        InlineTagInfo inlineTagInfoResult = null;
        for (int i = 0; i < inlineTagInfo.length; i++) {
            int thisIndex = inlineTagInfo[i].range.startIndex;
            if (thisIndex >= range.startIndex && thisIndex < range.endIndex) {
                // possibility
                inlineTagIndex = thisIndex;
                inlineTagInfoResult = inlineTagInfo[i];
                break;
            }
        }
        if (htmlTagIndex < 0 && inlineTagIndex < 0) {
            return null;
        } else {
            if (htmlTagIndex < 0) {
                return new InlineTagEntityIndex(inlineTagIndex, inlineTagInfoResult);
            } else if (inlineTagIndex < 0) {
                return new HtmlTagEntityIndex(htmlTagIndex);
            } else {
                // both possible
                if (htmlTagIndex < inlineTagIndex) {
                    return new HtmlTagEntityIndex(htmlTagIndex);
                } else {
                    return new InlineTagEntityIndex(inlineTagIndex, inlineTagInfoResult);
                }
            }
        }

    }

    /**
     * Find the tag that matches {@code tag} in string {@code range} starting at {@code index}.
     * @param range string range to be searched
     * @param tag
     * @param index index of first char after '>', i.e. the start tag
     * @return {@link HtmlTagData} or null if not found
     */
    private static HtmlTagData findmatchingTag(StringRange range, String tag, final int index) {
        int curIndex = index;
        int nestCount = 0;
        while (true) {
            int tagIndex = range.indexOf('<', curIndex);
            if (tagIndex < 0) {
                // no more tags, therefore no matching tag found in the given string range
                return null;
            }
            // find the end of this tag, which may be the starts of a nested tag or the matching end tag
            int tagEndIndex = range.indexOf('>', tagIndex);
            if (tagEndIndex < 0) {
                throw new WikiException("malformed HTML tag");
            }
            // check if this is a closing tag
            if (range.string.charAt(tagIndex + 1) == '/') {
                String endTag = range.string.substring(tagIndex + 2, tagEndIndex).toUpperCase(); // skip "</"
                if (endTag.equals(tag)) {
                    if (nestCount == 0) {
                        return new HtmlTagData(range, index, tagIndex, tagEndIndex + 1);
                    } else {
                        nestCount--;
                    }
                } else {
                    // we found the end of an unmatching nested tag, just keep going
                }
                curIndex = tagEndIndex + 1;
            } else {
                // start of nested tag, stack it if it matches "tag" else skip it.
                // <P> is special, any nested tag matches (<P> as separator style)
                String endTag = range.string.substring(tagIndex + 1, tagEndIndex).toUpperCase();
                if (tag.equals("P") && endTag.equals("P")) {
                    return new HtmlTagData(range, index, tagIndex, tagIndex);
                } else if (endTag.equals(tag)) {
                    nestCount++;
                }
                curIndex = tagEndIndex + 1;
            }
        }
    }

    private static int isHeader(String tagName) {
        Integer depth = headerDepth.get(tagName.toUpperCase());
        if (depth == null) {
            return -1;
        } else {
            return depth;
        }
    }

    private static String readOptions(String[][] options) {
        String tagName = null;
        for (int i = 0; i < options.length; i++) {
            String[] opt = options[i];
            if (opt[0].equals("-d")) {
                outputDir = opt[1];
            } else if (opt[0].equals("-notoc")) {
                includeToc = false;
            } else if (opt[0].equals("-docletpath")) {
                docletPath = opt[1];
            } else if (opt[0].equals("-projects")) {
                projectList = opt[1];
            }
        }
        return tagName;
    }

    public static int optionLength(String option) {
        if (option.equals("-d") || option.equals("-link")) {
            return 2;
        } else if (option.equals("-notoc")) {
            return 1;
        } else if (option.equals("-projects")) {
            return 2;
        }
        return 0;
    }

    public static boolean validOptions(String[][] options, DocErrorReporter reporter) {
        boolean foundDirOption = false;
        for (int i = 0; i < options.length; i++) {
            String[] opt = options[i];
            if (opt[0].equals("-d")) {
                if (foundDirOption) {
                    reporter.printError("Only one -d option allowed.");
                    return false;
                } else {
                    foundDirOption = true;
                }
            } else if (opt[0].equals("-notoc")) {
                // ignore
            } else if (opt[0].equals("-link")) {
                // ignore
            } else if (opt[0].equals("-projects")) {
                // ignore
            }
        }
        if (!foundDirOption) {
            reporter.printError("Usage: javadoc -doclet WikiDoclet -d outputDir -link url ...");
        }
        return foundDirOption;
    }

}
