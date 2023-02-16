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
package sc.fiji.io;

import ij.IJ;
import ij.ImagePlus;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import net.sf.image4j.codec.ico.ICODecoder;

public class ICO_Reader extends ImagePlus implements PlugIn {

	/** Expects path as argument, or will ask for it and then open the image.*/
	public void run(final String arg) {
		File file = null;
		if (arg != null && arg.length() > 0)
			file = new File(arg);
		else {
			OpenDialog od =
				new OpenDialog("Choose .ico file", null);
			String directory = od.getDirectory();
			if (null == directory)
				return;
			file = new File(directory + "/" + od.getFileName());
		}

		try {
			List<BufferedImage> image = ICODecoder.read(file);
			if (image.size() < 1)
				return;

			setTitle(file.getName());
			setImage(image.get(0));

			for (int i = 1; i < image.size(); i++)
				new ImagePlus(file.getName() + "-" + (i + 1),
					image.get(i)).show();
		} catch (IOException e) {
			IJ.error("Error reading file " + file.getAbsolutePath()
				+ ": " + e);
		}
	}
}
