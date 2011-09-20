package org.eclipse.jetty.webapp;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.PatternMatcher;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.JarResource;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;

public class WebInfConfiguration extends AbstractConfiguration
{
    private static final Logger LOG = Log.getLogger(WebInfConfiguration.class);

    public static final String TEMPDIR_CONFIGURED = "org.eclipse.jetty.tmpdirConfigured";
    public static final String CONTAINER_JAR_PATTERN = "org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern";
    public static final String WEBINF_JAR_PATTERN = "org.eclipse.jetty.server.webapp.WebInfIncludeJarPattern";

    /**
     * If set, to a list of URLs, these resources are added to the context resource base as a resource collection.
     */
    public static final String RESOURCE_URLS = "org.eclipse.jetty.resources";

    protected Resource _preUnpackBaseResource;

    @Override
    public void preConfigure(final WebAppContext context) throws Exception
    {
        //Make a temp directory for the webapp if one is not already set
        makeTempDirectory(context);

        //Extract webapp if necessary
        unpack(context);

        //Apply an initial ordering to the jars which governs which will be scanned for META-INF
        //info and annotations. The ordering is based on inclusion patterns.       
        String tmp = (String)context.getAttribute(WEBINF_JAR_PATTERN);
        Pattern webInfPattern = (tmp == null?null:Pattern.compile(tmp));
        tmp = (String)context.getAttribute(CONTAINER_JAR_PATTERN);
        Pattern containerPattern = (tmp == null?null:Pattern.compile(tmp));

        //Apply ordering to container jars - if no pattern is specified, we won't
        //match any of the container jars
        PatternMatcher containerJarNameMatcher = new PatternMatcher()
        {
            @Override
            public void matched(URI uri) throws Exception
            {
                context.getMetaData().addContainerJar(Resource.newResource(uri));
            }
        };
        ClassLoader loader = context.getClassLoader();
        while (loader != null && (loader instanceof URLClassLoader))
        {
            URL[] urls = ((URLClassLoader)loader).getURLs();
            if (urls != null)
            {
                URI[] containerUris = new URI[urls.length];
                int i = 0;
                for (URL u : urls)
                {
                    try
                    {
                        containerUris[i] = u.toURI();
                    }
                    catch (URISyntaxException e)
                    {
                        containerUris[i] = new URI(u.toString().replaceAll(" ","%20"));
                    }
                    i++;
                }
                containerJarNameMatcher.match(containerPattern,containerUris,false);
            }
            loader = loader.getParent();
        }

        //Apply ordering to WEB-INF/lib jars
        PatternMatcher webInfJarNameMatcher = new PatternMatcher()
        {
            @Override
            public void matched(URI uri) throws Exception
            {
                context.getMetaData().addWebInfJar(Resource.newResource(uri));
            }
        };
        List<Resource> jars = findJars(context);

        //Convert to uris for matching
        URI[] uris = null;
        if (jars != null)
        {
            uris = new URI[jars.size()];
            int i = 0;
            for (Resource r : jars)
            {
                uris[i++] = r.getURI();
            }
        }
        webInfJarNameMatcher.match(webInfPattern,uris,true); //null is inclusive, no pattern == all jars match 
    }

    @Override
    public void configure(WebAppContext context) throws Exception
    {
        //cannot configure if the context is already started
        if (context.isStarted())
        {
            if (LOG.isDebugEnabled())
            {
                LOG.debug("Cannot configure webapp " + context + " after it is started");
            }
            return;
        }

        Resource web_inf = context.getWebInf();

        // Add WEB-INF classes and lib classpaths
        if (web_inf != null && web_inf.isDirectory() && context.getClassLoader() instanceof WebAppClassLoader)
        {
            // Look for classes directory
            Resource classes = web_inf.addPath("classes/");
            if (classes.exists())
                ((WebAppClassLoader)context.getClassLoader()).addClassPath(classes);

            // Look for jars
            Resource lib = web_inf.addPath("lib/");
            if (lib.exists() || lib.isDirectory())
                ((WebAppClassLoader)context.getClassLoader()).addJars(lib);
        }

        // Look for extra resource
        @SuppressWarnings("unchecked")
        List<Resource> resources = (List<Resource>)context.getAttribute(RESOURCE_URLS);
        if (resources != null)
        {
            Resource[] collection = new Resource[resources.size() + 1];
            int i = 0;
            collection[i++] = context.getBaseResource();
            for (Resource resource : resources)
                collection[i++] = resource;
            context.setBaseResource(new ResourceCollection(collection));
        }
    }

    @Override
    public void deconfigure(WebAppContext context) throws Exception
    {
        // delete temp directory if we had to create it or if it isn't called work
        Boolean tmpdirConfigured = (Boolean)context.getAttribute(TEMPDIR_CONFIGURED);

        if (context.getTempDirectory() != null && (tmpdirConfigured == null || !tmpdirConfigured.booleanValue())
                && !isTempWorkDirectory(context.getTempDirectory()))
        {
            IO.delete(context.getTempDirectory());
            context.setTempDirectory(null);

            //clear out the context attributes for the tmp dir only if we had to
            //create the tmp dir
            context.setAttribute(TEMPDIR_CONFIGURED,null);
            context.setAttribute(WebAppContext.TEMPDIR,null);
        }

        //reset the base resource back to what it was before we did any unpacking of resources
        context.setBaseResource(_preUnpackBaseResource);
    }

    /* ------------------------------------------------------------ */
    /**
     * @see org.eclipse.jetty.webapp.AbstractConfiguration#cloneConfigure(org.eclipse.jetty.webapp.WebAppContext,
     *      org.eclipse.jetty.webapp.WebAppContext)
     */
    @Override
    public void cloneConfigure(WebAppContext template, WebAppContext context) throws Exception
    {
        Random random = new Random();
        boolean exists = true;
        File originalTempDir = context.getTempDirectory();
        File tmpDir = context.getTempDirectory();
        while (exists)
        {
            tmpDir = new File(originalTempDir.getParentFile(),originalTempDir.getName() + "-" + random.nextInt());
            exists = tmpDir.exists();
        }

        if (!tmpDir.mkdirs())
        {
            LOG.warn("Unable to create cloned Temp Directory: " + tmpDir.getAbsolutePath());
        }
        context.setTempDirectory(tmpDir);
    }

    /* ------------------------------------------------------------ */
    public void makeTempDirectory(WebAppContext context)
    {
        File dir = context.resolveUnpackDirectory();

        if (dir == null)
        {
            LOG.warn("Unable to create temp directory (directory not specified)");
            return;
        }

        if (!dir.exists())
        {
            if (!dir.mkdirs())
            {
                LOG.warn("Unable to create temp directory: " + dir.getAbsolutePath());
            }
        }
    }

    public void unpack(WebAppContext context) throws IOException
    {
        Resource web_app = context.getBaseResource();
        _preUnpackBaseResource = context.getBaseResource();

        if (web_app == null)
        {
            String war = context.getWar();
            if (war != null && war.length() > 0)
                web_app = context.newResource(war);
            else
                web_app = context.getBaseResource();

            // Accept aliases for WAR files
            if (web_app.getAlias() != null)
            {
                LOG.debug(web_app + " anti-aliased to " + web_app.getAlias());
                web_app = context.newResource(web_app.getAlias());
            }

            if (LOG.isDebugEnabled())
                LOG.debug("Try webapp=" + web_app + ", exists=" + web_app.exists() + ", directory=" + web_app.isDirectory());

            // Is the WAR usable directly?
            if (web_app.exists() && !web_app.isDirectory() && !web_app.toString().startsWith("jar:"))
            {
                // No - then lets see if it can be turned into a jar URL.
                Resource jarWebApp = JarResource.newJarResource(web_app);
                if (jarWebApp.exists() && jarWebApp.isDirectory())
                    web_app = jarWebApp;
            }

            // If we should extract or the URL is still not usable
            if (web_app.exists()
                    && ((context.isCopyWebDir() && web_app.getFile() != null && web_app.getFile().isDirectory())
                            || (context.isExtractWAR() && web_app.getFile() != null && !web_app.getFile().isDirectory())
                            || (context.isExtractWAR() && web_app.getFile() == null) || !web_app.isDirectory()))
            {
                // Look for sibling directory.
                File extractedWebAppDir = null;

                if (war != null)
                {
                    // look for a sibling like "foo/" to a "foo.war"
                    File warfile = Resource.newResource(war).getFile();
                    if (warfile != null && warfile.getName().toLowerCase().endsWith(".war"))
                    {
                        File sibling = new File(warfile.getParent(),warfile.getName().substring(0,warfile.getName().length() - 4));
                        if (sibling.exists() && sibling.isDirectory() && sibling.canWrite())
                            extractedWebAppDir = sibling;
                    }
                }

                if (extractedWebAppDir == null)
                    // Then extract it if necessary to the temporary location
                    extractedWebAppDir = new File(context.getTempDirectory(),"webapp");

                if (web_app.getFile() != null && web_app.getFile().isDirectory())
                {
                    // Copy directory
                    LOG.info("Copy " + web_app + " to " + extractedWebAppDir);
                    web_app.copyTo(extractedWebAppDir);
                }
                else
                {
                    if (!extractedWebAppDir.exists())
                    {
                        //it hasn't been extracted before so extract it
                        extractedWebAppDir.mkdir();
                        LOG.info("Extract " + web_app + " to " + extractedWebAppDir);
                        Resource jar_web_app = JarResource.newJarResource(web_app);
                        jar_web_app.copyTo(extractedWebAppDir);
                    }
                    else
                    {
                        //only extract if the war file is newer
                        if (web_app.lastModified() > extractedWebAppDir.lastModified())
                        {
                            IO.delete(extractedWebAppDir);
                            extractedWebAppDir.mkdir();
                            LOG.info("Extract " + web_app + " to " + extractedWebAppDir);
                            Resource jar_web_app = JarResource.newJarResource(web_app);
                            jar_web_app.copyTo(extractedWebAppDir);
                        }
                    }
                }
                web_app = Resource.newResource(extractedWebAppDir.getCanonicalPath());
            }

            // Now do we have something usable?
            if (!web_app.exists() || !web_app.isDirectory())
            {
                LOG.warn("Web application not found " + war);
                throw new java.io.FileNotFoundException(war);
            }

            context.setBaseResource(web_app);

            if (LOG.isDebugEnabled())
                LOG.debug("webapp=" + web_app);
        }

        // Do we need to extract WEB-INF/lib?
        if (context.isCopyWebInf())
        {
            Resource web_inf = web_app.addPath("WEB-INF/");

            if (web_inf instanceof ResourceCollection || web_inf.exists() && web_inf.isDirectory()
                    && (web_inf.getFile() == null || !web_inf.getFile().isDirectory()))
            {
                File extractedWebInfDir = new File(context.getTempDirectory(),"webinf");
                if (extractedWebInfDir.exists())
                    IO.delete(extractedWebInfDir);
                extractedWebInfDir.mkdir();
                Resource web_inf_lib = web_inf.addPath("lib/");
                File webInfDir = new File(extractedWebInfDir,"WEB-INF");
                webInfDir.mkdir();

                if (web_inf_lib.exists())
                {
                    File webInfLibDir = new File(webInfDir,"lib");
                    if (webInfLibDir.exists())
                        IO.delete(webInfLibDir);
                    webInfLibDir.mkdir();

                    LOG.info("Copying WEB-INF/lib " + web_inf_lib + " to " + webInfLibDir);
                    web_inf_lib.copyTo(webInfLibDir);
                }

                Resource web_inf_classes = web_inf.addPath("classes/");
                if (web_inf_classes.exists())
                {
                    File webInfClassesDir = new File(webInfDir,"classes");
                    if (webInfClassesDir.exists())
                        IO.delete(webInfClassesDir);
                    webInfClassesDir.mkdir();
                    LOG.info("Copying WEB-INF/classes from " + web_inf_classes + " to " + webInfClassesDir.getAbsolutePath());
                    web_inf_classes.copyTo(webInfClassesDir);
                }

                web_inf = Resource.newResource(extractedWebInfDir.getCanonicalPath());

                ResourceCollection rc = new ResourceCollection(web_inf,web_app);

                if (LOG.isDebugEnabled())
                    LOG.debug("context.resourcebase = " + rc);

                context.setBaseResource(rc);
            }
        }
    }

    public File findWorkDirectory(WebAppContext context) throws IOException
    {
        if (context.getBaseResource() != null)
        {
            Resource web_inf = context.getWebInf();
            if (web_inf != null && web_inf.exists())
            {
                return new File(web_inf.getFile(),"work");
            }
        }
        return null;
    }

    /**
     * Check if the tmpDir itself is called "work", or if the tmpDir is in a directory called "work".
     * 
     * @return true if File is a temporary or work directory
     */
    public boolean isTempWorkDirectory(File tmpDir)
    {
        if (tmpDir == null)
            return false;
        if (tmpDir.getName().equalsIgnoreCase("work"))
            return true;
        File t = tmpDir.getParentFile();
        if (t == null)
            return false;
        return (t.getName().equalsIgnoreCase("work"));
    }

    /**
     * Look for jars in WEB-INF/lib
     * 
     * @param context
     * @return the list of jar resources found within context
     * @throws Exception
     */
    protected List<Resource> findJars(WebAppContext context) throws Exception
    {
        List<Resource> jarResources = new ArrayList<Resource>();

        Resource web_inf = context.getWebInf();
        if (web_inf == null || !web_inf.exists())
            return null;

        Resource web_inf_lib = web_inf.addPath("/lib");

        if (web_inf_lib.exists() && web_inf_lib.isDirectory())
        {
            String[] files = web_inf_lib.list();
            for (int f = 0; files != null && f < files.length; f++)
            {
                try
                {
                    Resource file = web_inf_lib.addPath(files[f]);
                    String fnlc = file.getName().toLowerCase();
                    int dot = fnlc.lastIndexOf('.');
                    String extension = (dot < 0?null:fnlc.substring(dot));
                    if (extension != null && (extension.equals(".jar") || extension.equals(".zip")))
                    {
                        jarResources.add(file);
                    }
                }
                catch (Exception ex)
                {
                    LOG.warn(Log.EXCEPTION,ex);
                }
            }
        }
        return jarResources;
    }
}
