/**
 *  JsonRandomAccessFile
 *  Copyright 2015 by Michael Peter Christen
 *  First released 04.10.2015
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */


package org.loklak.tools.storage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;

import org.json.JSONObject;
import org.loklak.data.DAO;
import org.loklak.tools.BufferedRandomAccessFile;
import org.loklak.tools.UTF8;

public class JsonRandomAccessFile extends BufferedRandomAccessFile implements JsonReader {

    private File file;
    private int concurrency;
    private ArrayBlockingQueue<JsonFactory> jsonline;
    
    /**
     * if a JsonRandomAccessFile object in initiated, it must be wrapped with a Thread object and started.
     * @param dumpFile
     * @param concurrency
     * @throws IOException
     */
    public JsonRandomAccessFile(final File dumpFile, final int concurrency) throws IOException {
        super(dumpFile, "rw");
        this.file = dumpFile;
        this.concurrency = concurrency;
        this.jsonline = new ArrayBlockingQueue<>(1000);
    }
    
    public String getName() {
        return this.file.getAbsolutePath();
    }
    
    public int getConcurrency() {
        return this.concurrency;
    }
    
    public JsonFactory take() throws InterruptedException {
        return this.jsonline.take();
    }

    public void run() {
        try {
            BufferedRandomAccessFile.IndexedLine line;
            while ((line = this.readIndexedLine()) != null) {
                try {
                    byte[] textb = line.getText();
                    if (textb == null || textb.length == 0) continue;
                    this.jsonline.put(new JsonHandle(textb, line.getPos(), textb.length));
                } catch (Throwable e) {
                    DAO.severe("cannot parse line in file " + JsonRandomAccessFile.this.file + ": \"" + line + "\"", e);
                }
            }
        } catch (IOException e) {
        	DAO.severe(e);
        } finally {
            for (int i = 0; i < this.concurrency; i++) {
                try {this.jsonline.put(JsonReader.POISON_JSON_MAP);} catch (InterruptedException e) {}
            }
        }
    }
    
    /**
     * The JsonHandle class is a bundle of a json with the information about the
     * seek location in the file and the length of bytes of the original json string
     */
    public static class JsonHandle implements JsonFactory {
        private Object obj;
        private long index;
        private int length;
        public JsonHandle(Object obj, long index, int length) {
            this.obj = obj;
            this.index = index;
            this.length = length;
        }
        public JSONObject getJSON() {
            Object o = this.obj;
            if (o == null) {
                return null;
            } else if (o instanceof JSONObject) {
                return (JSONObject) o;
            } else if (o instanceof byte[]) {
                JSONObject json = new JSONObject(new String((byte[]) o, StandardCharsets.UTF_8));
                this.obj = json;
                return json;
            } else if (o instanceof String) {
                JSONObject json = new JSONObject((String) o);
                this.obj = json;
                return json;
            }
            return null;
        }
        public long getIndex() {
            return index;
        }
        public int getLength() {
            return length;
        }
        public String toString() {
            Object o = this.obj;
            if (o == null) {
                return null;
            } else if (o instanceof JSONObject) {
                return ((JSONObject) o).toString();
            } else if (o instanceof byte[]) {
                String s = new String((byte[]) o, StandardCharsets.UTF_8);
                this.obj = s;
                return s;
            } else if (o instanceof String) {
                return (String) o;
            }
            return null;
        }
        public String getString() {
            return this.toString();
        }
    }
    
    public JsonFactory getJsonFactory(long index, int length) {
        return new ReaderJsonFactory(index, length);
    }
    
    public class ReaderJsonFactory implements JsonFactory {

        private long index;
        private int length;
        
        public ReaderJsonFactory(long index, int length) {
            this.index = index;
            this.length = length;
        }
        
        @Override
        public JSONObject getJSON() throws IOException {
            byte[] b = new byte[this.length];
            JsonRandomAccessFile.this.read(b, this.index);
            return new JSONObject(UTF8.String(b));
        }
        public long getIndex() {
            return this.index;
        }
        public int getLength() {
            return this.length;
        }
        public File getFile() {
            return JsonRandomAccessFile.this.file;
        }
        public String toString() {
            try {
                return this.getJSON().toString();
            } catch (IOException e) {
            	DAO.severe(e);
                return "";
            }
        }
        public String getString() {
            return this.toString();
        }
    }
    
    public void close() throws IOException {
        super.close();
    }

}
