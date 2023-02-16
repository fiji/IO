/*-
 * #%L
 * IO plugin for Fiji.
 * %%
 * Copyright (C) 2008 - 2023 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
// Save the active image as .ico file
package sc.fiji.io;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.io.SaveDialog;
import ij.plugin.PlugIn;

import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;

import net.sf.image4j.codec.ico.ICOEncoder;

public class ICO_Writer implements PlugIn {

	public void run (String arg) {
		ImagePlus image = WindowManager.getCurrentImage();
		if (image == null) {
			IJ.showStatus("No image is open");
			return;
		}

		// TODO: support saving more than one image

		String path = arg;
		if (path == null || path.length() < 1) {
			String name = image.getTitle();
			SaveDialog sd = new SaveDialog("Save as ICO",
					name, ".ico");
			String directory = sd.getDirectory();
			if (directory == null)
				return;

			if (!directory.endsWith("/"))
				directory += "/";
			name = sd.getFileName();
			path = directory + name;
		}

		try {
			FileOutputStream out = new FileOutputStream(path);
			ICOEncoder.write((BufferedImage)image.getImage(), out);
			out.close();
		} catch (IOException e) {
			IJ.error("Failed to write " + path + ": " + e);
		}
	}
}
