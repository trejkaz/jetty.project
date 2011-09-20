package org.eclipse.jetty.webapp;

import java.io.File;

import org.eclipse.jetty.toolchain.test.FS;
import org.eclipse.jetty.toolchain.test.TestingDir;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class UnpackDirTest
{
    @Rule
    public TestingDir testdir = new TestingDir();

    /**
     * Ensure that the classic behavior for determining the unpack directory works properly.
     */
    @Test
    public void testClassicBehavior_JettyHomeWorkDir()
    {
        testdir.ensureEmpty();
        File jettyHomeWorkDir = testdir.getFile("work");
        FS.ensureDirExists(jettyHomeWorkDir);

        UnpackDir upackdir = new UnpackDir(UnpackStrategy.CLASSIC);
        upackdir.setJettyHome(testdir.getDir());

        WebAppContext context = new WebAppContext();
        context.setResourceBase(jettyHomeWorkDir.toURI().toASCIIString());

        File actualPath = upackdir.getDir(context);
        File expectedPath = new File(jettyHomeWorkDir,"jetty-work-_-any-");
        // System.out.printf("Unpack Dir: %s%n",actualPath);
        Assert.assertEquals(expectedPath,actualPath);
    }

    /**
     * Ensure that the classic behavior for determining the unpack directory works properly.
     */
    @Test
    public void testClassicBehavior_ContextTempDirectory()
    {
        testdir.ensureEmpty();
        File ctxTempDir = testdir.getFile("ctxtmpdir");
        FS.ensureDirExists(ctxTempDir);

        UnpackDir upackdir = new UnpackDir(UnpackStrategy.CLASSIC);
        upackdir.setJettyHome(testdir.getDir());

        WebAppContext context = new WebAppContext();
        File resDir = testdir.getFile("resdir");
        context.setResourceBase(resDir.toURI().toASCIIString());
        context.setTempDirectory(ctxTempDir);

        File actualPath = upackdir.getDir(context);
        // System.out.printf("Unpack Dir: %s%n",actualPath);
        Assert.assertEquals(ctxTempDir,actualPath);
    }

    @Test
    public void testGetWorkDirectory_SystemTempDir()
    {
        testdir.ensureEmpty();

        UnpackDir upackdir = new UnpackDir(UnpackStrategy.CLASSIC);
        upackdir.setJettyHome(testdir.getDir()); // set ${jetty.home}
        WebAppContext context = new WebAppContext();
        File work = upackdir.findWorkDirectory(context);

        // No ${jetty.home}/work
        // No context attribute
        // Should fall back to system temp directory
        File systemTempDir = new File(System.getProperty("java.io.tmpdir"));

        Assert.assertEquals("Work Directory",systemTempDir.getAbsolutePath(),work.getAbsolutePath());
    }

    @Test
    public void testGetWorkDirectory_ContextAttribute()
    {
        testdir.ensureEmpty();

        File baseWorkDir = testdir.getFile("baseworkdir");
        FS.ensureDirExists(baseWorkDir);

        UnpackDir upackdir = new UnpackDir(UnpackStrategy.CLASSIC);
        upackdir.setJettyHome(testdir.getDir()); // set ${jetty.home}
        WebAppContext context = new WebAppContext();
        context.setAttribute(WebAppContext.BASETEMPDIR,baseWorkDir);
        File work = upackdir.findWorkDirectory(context);

        // No ${jetty.home}/work
        // Context attribute exists, use it

        Assert.assertEquals("Work Directory",baseWorkDir.getAbsolutePath(),work.getAbsolutePath());
    }

    @Test
    public void testGetWorkDirectory_JettyHomeWithWorkDir()
    {
        testdir.ensureEmpty();
        File jettyHomeWorkDir = testdir.getFile("work");
        FS.ensureDirExists(jettyHomeWorkDir);

        UnpackDir upackdir = new UnpackDir(UnpackStrategy.CLASSIC);
        upackdir.setJettyHome(testdir.getDir());
        WebAppContext context = new WebAppContext();
        File work = upackdir.findWorkDirectory(context);

        // ${jetty.home}/work exists, use it
        Assert.assertEquals("Work Directory",jettyHomeWorkDir.getAbsolutePath(),work.getAbsolutePath());
    }
}
