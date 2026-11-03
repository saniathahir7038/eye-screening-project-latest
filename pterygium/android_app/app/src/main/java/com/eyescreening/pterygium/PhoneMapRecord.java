package com.eyescreening.pterygium;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Keeps optional calculated maps in their own folder under an existing capture. */
final class PhoneMapRecord {
    private PhoneMapRecord() {}
    private static File directory(File files,String id)throws IOException {
        try {
            if(id==null||!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException();
        }catch(IllegalArgumentException error){throw new IOException("Invalid capture ID");}
        File root=new File(files,"smartkc-captures").getCanonicalFile();
        if(!root.getParentFile().equals(files.getCanonicalFile()))throw new IOException("Capture root is outside app storage");
        File record=new File(root,id).getCanonicalFile();
        if(!record.getParentFile().equals(root))throw new IOException("Capture is outside saved records");
        return record;
    }
    static File source(File files,String id)throws IOException {
        File directory=directory(files,id),source=new File(directory,"source_image").getCanonicalFile();
        if(!source.getParentFile().equals(directory)||!source.isFile())throw new IOException("Saved source photo is unavailable");
        return source;
    }
    static File results(File files,String id)throws IOException {
        File directory=directory(files,id),result=new File(directory,"phone-maps").getCanonicalFile();
        if(!result.getParentFile().equals(directory))throw new IOException("Results folder is outside the selected capture");
        return result;
    }
}
