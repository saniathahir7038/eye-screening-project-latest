package com.eyescreening.pterygium;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.Files;

public class PhoneMapRecordTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private static final String ID="ab8904d6-5109-45d3-a933-60a6c74e8314";
    @Test public void existingSourceIsReadOnlyAndResultsAreSeparate()throws Exception{
        File files=temporary.newFolder("files"),record=new File(files,"smartkc-captures/"+ID);assertTrue(record.mkdirs());
        File source=new File(record,"source_image");byte[] data={7,8,9};Files.write(source.toPath(),data);
        assertEquals(source.getCanonicalFile(),PhoneMapRecord.source(files,ID));
        File result=PhoneMapRecord.results(files,ID);
        assertEquals("phone-maps",result.getName());assertEquals(record.getCanonicalFile(),result.getParentFile());
        assertFalse(result.exists());assertArrayEquals(data,Files.readAllBytes(source.toPath()));
    }
    @Test(expected=IOException.class)public void missingSourceRejected()throws Exception{PhoneMapRecord.source(temporary.newFolder("files"),ID);}
    @Test(expected=IOException.class)public void traversalCannotSelectSource()throws Exception{PhoneMapRecord.source(temporary.newFolder("files"),"../../source_image");}
    @Test(expected=IOException.class)public void arbitraryDirectoryCannotBecomeResults()throws Exception{PhoneMapRecord.results(temporary.newFolder("files"),"../measured-reconstruction");}
}
