/*
 * Copyright 2016 Finn Bock
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.bckfnn.ftp;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FtpFile {
	private static Logger log = LoggerFactory.getLogger(File.class);
	
	private static Pattern filere = Pattern.compile("(.)......... +\\d+ +\\d+ +\\d+ +(\\d+) ([A-Z][a-z][a-z] +\\d+ +(?:(?:\\d\\d:\\d\\d)|(?:\\d+))) (.*)");

	private String type;
	private String name;
	private long size;
	private String date;


	public String getType() {
		return type;
	}

	public String getName() {
		return name;
	}

	public long getSize() {
		return size;
	}

	public String getDate() {
		return date;
	}

	public boolean isDirectory() {
		return type.equals("d");
	}
	
	public static FtpFile from(String line) {
		Matcher m = filere.matcher(line);
		//log.info("file:" + f.toString());
		if (!m.matches()) {
			log.error("line " + line + " does not match dir re");
			return null;
		} else {
			String size = m.group(2);
			String date = m.group(3);
			String fname = m.group(4);

			FtpFile f = new FtpFile();
			f.type = m.group(1);
			f.size = Long.parseLong(size);
			f.name = fname;
			f.date = date;
			return f;
		}
	}

	public static List<FtpFile> listing(String listing) throws Exception {
		List<FtpFile> ret  = new ArrayList<>();

		for (String f : listing.split("\n")) {
			FtpFile file = FtpFile.from(f.toString());

			if (file == null) {
				continue;
			}
			if (file.name.equals(".") || file.name.equals("..")) {
				continue;
			}

			ret.add(file);
		}
		return ret;
	}



}

