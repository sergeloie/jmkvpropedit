/*
 * Copyright (c) 2012-2013 Bruno Barbieri
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

package ru.anseranser.jmkvpropedit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/*
 * Original code by Michael C. Daconta
 * Source: http://www.javaworld.com/javaworld/jw-12-2000/jw-1229-traps.html?page=4
 *
 */

public class StreamGobbler extends Thread {
    private final InputStream is;
    private final JTextArea text;

    public StreamGobbler(InputStream is, JTextArea text) {
        this.is = is;
        this.text = text;
    }

    @Override
    public void run() {
        // Decode the process output as explicit UTF-8 instead of the platform
        // default charset; try-with-resources closes the stream so the thread
        // finishes as soon as the process hits EOF.
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;

            while ((line = br.readLine()) != null) {
                appendToLog(line + "\n");
            }
        } catch (IOException e) {
            appendToLog(e.toString());
            e.printStackTrace();
        }
    }

    /**
     * Marshals the append to the EDT: this reader runs on its own thread and
     * must never touch the JTextArea directly.
     */
    private void appendToLog(final String s) {
        SwingUtilities.invokeLater(() -> {
            text.append(s);
            text.setCaretPosition(text.getDocument().getLength()); // Autoscroll
        });
    }
}