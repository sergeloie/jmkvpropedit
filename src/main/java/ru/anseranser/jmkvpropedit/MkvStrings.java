package ru.anseranser.jmkvpropedit;/*
 * Copyright (c) 2012-2018 Bruno Barbieri
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list
 * of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list
 * of conditions and the following disclaimer in the documentation and/or other materials
 * provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */



import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

public class MkvStrings {
    /*
     * ISO639 language names and codes
     * Taken from src/common/iso639_language_list.cpp, part of mkvtoolnix by Moritz Bunkus
     *
     */
    public String[] getLangNames() {
        return langNameList.toArray(String[]::new);
    }

    public String[] getLangCodes() {
        return langCodeList.toArray(String[]::new);
    }

    /*
     * MIME Types
     * Taken from freedesktop.org.xml.in, part of shared-mime-info
     * from freedesktop.org
     *
     */
    public String[] getMimeTypes() {
        return mimeTypeList.toArray(String[]::new);
    }

    private final List<String> langNameList = readLines("langnames.txt");
    private final List<String> langCodeList = readLines("langcodes.txt");
    private final List<String> mimeTypeList = readLines("mimetypes.txt");

    public List<String> getLangNameList() {
        return langNameList;
    }
    public List<String> getLangCodeList() {
        return langCodeList;
    }
    public List<String> getMimeTypeList() {
        return mimeTypeList;
    }

    public static List<String> readLines(String resourceName) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        MkvStrings.class.getClassLoader().getResourceAsStream(resourceName)
                )
        )) {
            return reader.lines().collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Не удалось прочитать ресурс: " + resourceName, e);
        }
    }
}
