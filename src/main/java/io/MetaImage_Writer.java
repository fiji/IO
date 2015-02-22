/**
    MetaImage writer plugin for ImageJ.

    This plugin writes MetaImage text-based tagged format files.

    Author: Kang Li (kangli AT cs.cmu.edu)

    Installation:
      Download MetaImage_Reader_Writer.jar to the plugins folder, or subfolder.
      Restart ImageJ, and there will be new File/Import/MetaImage... and
      File/Save As/MetaImage... commands.

    History:
      2007/04/07: First version

    References:
      MetaIO Documentation (http://www.itk.org/Wiki/MetaIO/Documentation)
*/

/**
Copyright (C) 2007-2008 Kang Li. All rights reserved.

Permission to use, copy, modify, and distribute this software for any purpose
without fee is hereby granted,  provided that this entire notice  is included
in all copies of any software which is or includes a copy or modification  of
this software  and in  all copies  of the  supporting documentation  for such
software. Any for profit use of this software is expressly forbidden  without
first obtaining the explicit consent of the author.

THIS  SOFTWARE IS  BEING PROVIDED  "AS IS",  WITHOUT ANY  EXPRESS OR  IMPLIED
WARRANTY.  IN PARTICULAR,  THE AUTHOR  DOES NOT  MAKE ANY  REPRESENTATION OR
WARRANTY OF ANY KIND CONCERNING  THE MERCHANTABILITY OF THIS SOFTWARE  OR ITS
FITNESS FOR ANY PARTICULAR PURPOSE.
*/

import java.io.*;
import java.awt.*;
import ij.*;
import ij.gui.*;
import ij.io.*;
import ij.plugin.*;
import ij.process.*;
import ij.measure.Calibration;

//  This plugin saves MetaImage format files.
//  It appends the '.mhd' and '.raw' suffixes to the header and data files, respectively.
//

public final class MetaImage_Writer implements PlugIn {

    public void run(String arg) {
        ImagePlus imp = WindowManager.getCurrentImage();
        if (imp == null) {
            IJ.noImage();
            return;
        }
        if (imp.getCalibration().isSigned16Bit() && IJ.versionLessThan("1.34e")) {
            IJ.error("MetaImage Reader: Please upgrade to ImageJ v1.34e or later.");
            return;
        }
        String dir = "", baseName = "";
        if (arg == null || arg.length() == 0) {
            SaveDialog sd = new SaveDialog(
              "Save as MetaImage", imp.getTitle(), ".mhd");
            dir = sd.getDirectory();
            baseName = sd.getFileName();
        }
        else {
            File file = new File(arg);
            if (file.isDirectory()) {
                dir = arg;
                baseName = imp.getTitle();
            }
            else {
                dir = file.getParent();
                baseName = file.getName();
            }
        }

        if (baseName == null || baseName.length() == 0)
            return;

        int baseLength = baseName.length();
        String lowerBaseName = baseName.toLowerCase();
        if (lowerBaseName.endsWith(".mhd") || lowerBaseName.endsWith(".mda"))
            baseName = baseName.substring(0, baseLength - 4);
        save(imp, dir, baseName);
        IJ.showStatus(baseName + " saved");
    }


    private void save(ImagePlus imp, String dir, String baseName) {

        if (!dir.endsWith(File.separator) && dir.length() > 0)
            dir += File.separator;

        try {
            // Save header file.
            String headerName = baseName + ".mhd";
            String dataName = baseName + ".raw";
            IJ.showStatus("Saving " + headerName + "...");
            if (writeHeader(imp, dir + headerName, dataName)) {
                // Save data file.
                IJ.showStatus("Writing " + dataName + "...");
                if (imp.getStackSize() > 1)
                    new FileSaver(imp).saveAsRawStack(dir + dataName);
                else
                    new FileSaver(imp).saveAsRaw(dir + dataName);
            }
        }
        catch (IOException e) {
            IJ.error("MetaImage_Writer: " + e.getMessage());
        }
    }


    private boolean writeHeader(ImagePlus imp, String path, String dataFile)
        throws IOException
    {
        FileInfo fi = imp.getFileInfo();
        String numChannels = "1", type = "MET_NONE";

        switch (fi.fileType) {
        case FileInfo.COLOR8:          type = "MET_UCHAR";  break;
        case FileInfo.GRAY8:           type = "MET_UCHAR";  break;
        case FileInfo.GRAY16_SIGNED:   type = "MET_SHORT";  break;
        case FileInfo.GRAY16_UNSIGNED: type = "MET_USHORT"; break;
        case FileInfo.GRAY32_INT:      type = "MET_INT";    break;
        case FileInfo.GRAY32_UNSIGNED: type = "MET_UINT";   break;
        case FileInfo.GRAY32_FLOAT:    type = "MET_FLOAT";  break;
        case FileInfo.RGB:
            type = "MET_UCHAR_ARRAY";  numChannels = "3";
            break;
        case FileInfo.RGB48:
            type = "MET_USHORT_ARRAY"; numChannels = "3";
            break;
        default:
            throw new IOException(
                "Unsupported data format.");
        }

        FileOutputStream file = new FileOutputStream(path);
        PrintStream stream = new PrintStream(file);
        
        int ndims = (imp.getStackSize() > 1) ? 3 : 2;

        stream.println("ObjectType = Image");
        if (ndims == 3)
            stream.println("NDims = 3");
        else
            stream.println("NDims = 2");
        stream.println("BinaryData = True");
        if (fi.intelByteOrder)
            stream.println("BinaryDataByteOrderMSB = True");
        if (ndims == 3) {
            stream.println("DimSize = " + fi.width + " " + fi.height + " " + fi.nImages);
            stream.println("ElementSize = " + fi.pixelWidth + " " + fi.pixelHeight + " " + fi.pixelDepth);
        }
        else {
            stream.println("DimSize = " + fi.width + " " + fi.height);
            stream.println("ElementSize = " + fi.pixelWidth + " " + fi.pixelHeight);
        }
        if (numChannels != "1")
            stream.println("ElementNumberOfChannels = " + numChannels);
        stream.println("ElementType = " + type);
        stream.println("ElementDataFile = " + dataFile);

        stream.close();
        file.close();

        return true;
    }
}
