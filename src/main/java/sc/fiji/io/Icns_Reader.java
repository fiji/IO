/*-
 * #%L
 * IO plugin for Fiji.
 * %%
 * Copyright (C) 2008 - 2021 Fiji developers.
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
import java.io.FileInputStream;
import java.io.IOException;

import sc.fiji.io.icns.IcnsCodec;
import sc.fiji.io.icns.IconSuite;

public class Icns_Reader extends ImagePlus implements PlugIn {

	/** Expects path as argument, or will ask for it and then open the image.*/
	public void run(final String arg) {
		File file = null;
		if (arg != null && arg.length() > 0)
			file = new File(arg);
		else {
			OpenDialog od =
				new OpenDialog("Choose .icns file", null);
			String directory = od.getDirectory();
			if (null == directory)
				return;
			file = new File(directory + "/" + od.getFileName());
		}

		try {
			FileInputStream in = new FileInputStream(file);
			IcnsCodec codec = new IcnsCodec();
			IconSuite icons = codec.decode(in);
			alreadyShown = 0;
			show(file.getName(), icons.getThumbnailIcon());
			show(file.getName(), icons.getHugeIcon());
			show(file.getName(), icons.getLargeIcon());
			show(file.getName(), icons.getSmallIcon());
		} catch (IOException e) {
			IJ.error("Error reading file " + file.getAbsolutePath()
				+ ": " + e);
		}
	}

	private int alreadyShown;

	private void show(String name, BufferedImage image) {
		if (image == null)
			return;

		if (alreadyShown > 0)
			new ImagePlus(name + "-" + (++alreadyShown),
					image).show();
		else {
			setTitle(name);
			setImage(image);
			alreadyShown++;
		}
	}
}
