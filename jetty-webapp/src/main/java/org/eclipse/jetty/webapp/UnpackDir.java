package org.eclipse.jetty.webapp;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.URIUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.Resource;

public class UnpackDir
{
    private static final Logger LOG = Log.getLogger(UnpackDir.class);
    /**
     * The ${jetty.home} directory
     */
    private File jettyHome;
    private UnpackStrategy strategy;

    public UnpackDir()
    {
        this(UnpackStrategy.CLASSIC);
    }

    /**
     * Find the work directory.
     * <p>
     * Search order:
     * <ol>
     * <li>Use ${jetty.home}/work</li>
     * <li>Use {@link WebAppContext#getAttribute(String)} with key "org.eclipse.jetty.webapp.basetempdir"</li>
     * <li>Use System Temp Directory from {@link System#getProperty(String)} with key "java.io.tmpdir"</li>
     * </ol>
     * 
     * @param context
     *            the context to look in for the attribute configured directory.
     * @return the work directory (if found)
     * @throws IllegalStateException
     *             if unable to establish a work directory.
     */
    public File findWorkDirectory(WebAppContext context)
    {
        File work;

        // Look for "${jetty.home}/work" dir
        work = new File(getJettyHome(),"work");
        if (isValidDirectory(work))
        {
            return work;
        }

        // Look for servlet attribute "org.eclipse.jetty.webapp.basetempdir"
        work = asFile(context.getAttribute(WebAppContext.BASETEMPDIR));
        if (isValidDirectory(work))
        {
            return work;
        }

        // Use the system temp directory
        work = new File(System.getProperty("java.io.tmpdir"));
        if (isValidDirectory(work))
        {
            return work;
        }

        // Last Resort
        try
        {
            work = File.createTempFile("JettyContext","work");
            if (!work.exists())
            {
                if (!work.mkdirs())
                {
                    String err = "Unable to create WorkDirectory: " + work.getAbsolutePath();
                    LOG.warn(err);
                    throw new IllegalStateException(err);
                }
            }
            return work;
        }
        catch (IOException e)
        {
            String err = "Unable to establish WorkDirectory: " + work.getAbsolutePath();
            LOG.warn(err,e);
            throw new IllegalStateException(err);
        }
    }

    private File asFile(Object attr)
    {
        if (attr == null)
        {
            return null;
        }
        if (attr instanceof File)
        {
            return (File)attr;
        }
        if (attr instanceof String)
        {
            return new File((String)attr);
        }
        return null;
    }

    private boolean isValidDirectory(File path)
    {
        return ((path != null) && path.exists() && path.isDirectory() && path.canWrite());
    }

    public File getJettyHome()
    {
        if (jettyHome == null)
        {
            jettyHome = new File(System.getProperty("jetty.home"));
        }
        return jettyHome;
    }

    public void setJettyHome(File jettyHome)
    {
        this.jettyHome = jettyHome;
    }

    public UnpackDir(UnpackStrategy strategy)
    {
        this.strategy = strategy;
    }

    public UnpackStrategy getStrategy()
    {
        return strategy;
    }

    /**
     * Get the Unpack Directory for this context, using the specified strategy if need be.
     * <p>
     * NOTE: the {@link WebAppContext#setTempDirectory(File)} is guarunteed to be set by this method (if currently
     * unset)
     * 
     * @param context
     *            the context to base the unpack directory off of.
     * @return the directory for this webapp context.
     */
    public File getDir(WebAppContext context)
    {
        // Check for context provided temp directory
        File dir = context.getTempDirectory();
        if (isValidDirectory(dir))
        {
            // TODO: See if needed anymore.
            context.setAttribute(WebInfConfiguration.TEMPDIR_CONFIGURED,Boolean.TRUE);
            return dir;
        }

        // Check for servlet spec provided attribute
        dir = asFile(context.getAttribute(WebAppContext.TEMPDIR));
        if (isValidDirectory(dir))
        {
            context.setAttribute(WebAppContext.TEMPDIR,dir);
            context.setTempDirectory(dir);
            return dir;
        }

        // Use work directory + naming strategy
        File workdir = findWorkDirectory(context);
        String pathName = "";
        switch (strategy)
        {
            case RANDOM:
                Random random = new Random();
                boolean exists = true;
                while (exists)
                {
                    pathName = getClassicCanonicalName(context) + "-" + random.nextInt();
                    dir = new File(workdir,pathName);
                    exists = dir.exists();
                }
                break;
            case TIMESTAMP:
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss-S");
                String timestamp = sdf.format(new Date());
                pathName = getClassicCanonicalName(context) + "-" + timestamp;
                dir = new File(workdir,pathName);
                break;
            case CLASSIC:
            default:
                pathName = getClassicCanonicalName(context);
                dir = new File(workdir,pathName);
                break;
        }

        context.setAttribute(WebAppContext.TEMPDIR,dir);
        context.setTempDirectory(dir);
        return dir;
    }

    /**
     * Create a canonical name for a webapp temp directory. The form of the name is:
     * <code>"jetty-"+host+"_"+port+"__"+resourceBase+"_"+context+"_"+virtualhost+base36_hashcode_of_whole_string</code>
     * 
     * host and port uniquely identify the server context and virtual host uniquely identify the webapp
     * 
     * @param context
     *            the context to base the name off of.
     * @return the canonical name for the webapp temp directory
     */
    private String getClassicCanonicalName(WebAppContext context)
    {
        StringBuffer canonicalName = new StringBuffer();
        canonicalName.append("jetty-");

        // get the host and the port from the first connector 
        Server server = context.getServer();
        if (server != null)
        {
            Connector[] connectors = context.getServer().getConnectors();

            if (connectors.length > 0)
            {
                // Get the host
                String host = (connectors == null || connectors[0] == null?"":connectors[0].getHost());
                if (host == null)
                {
                    host = "0.0.0.0";
                }
                canonicalName.append(host);

                // Get the port
                canonicalName.append("-");
                // try getting the real port being listened on
                int port = (connectors == null || connectors[0] == null?0:connectors[0].getLocalPort());
                // if not available (eg no connectors or connector not started), 
                // try getting one that was configured.
                if (port < 0)
                {
                    port = connectors[0].getPort();
                }
                canonicalName.append(port);
                canonicalName.append("-");
            }
        }

        // Resource  base
        try
        {
            Resource resource = context.getBaseResource();
            if (resource == null)
            {
                if (context.getWar() == null || context.getWar().length() == 0)
                {
                    resource = context.newResource(context.getResourceBase());
                }

                // Set dir or WAR
                resource = context.newResource(context.getWar());
            }

            String tmp = URIUtil.decodePath(resource.getURL().getPath());
            if (tmp.endsWith("/"))
            {
                tmp = tmp.substring(0,tmp.length() - 1);
            }
            if (tmp.endsWith("!"))
            {
                tmp = tmp.substring(0,tmp.length() - 1);
            }
            //get just the last part which is the filename
            int i = tmp.lastIndexOf("/");
            canonicalName.append(tmp.substring(i + 1,tmp.length()));
            canonicalName.append("-");
        }
        catch (Exception e)
        {
            LOG.warn("Can't generate resourceBase as part of webapp tmp dir name",e);
        }

        // Context name
        String contextPath = context.getContextPath();
        contextPath = contextPath.replace('/','_');
        contextPath = contextPath.replace('\\','_');
        canonicalName.append(contextPath);

        //Virtual host (if there is one)
        canonicalName.append("-");
        String[] vhosts = context.getVirtualHosts();
        if (vhosts == null || vhosts.length <= 0)
        {
            canonicalName.append("any");
        }
        else
        {
            canonicalName.append(vhosts[0]);
        }

        // sanitize
        for (int i = 0; i < canonicalName.length(); i++)
        {
            char c = canonicalName.charAt(i);
            if (!Character.isJavaIdentifierPart(c) && "-.".indexOf(c) < 0)
            {
                canonicalName.setCharAt(i,'.');
            }
        }

        canonicalName.append("-");
        return canonicalName.toString();
    }
}
