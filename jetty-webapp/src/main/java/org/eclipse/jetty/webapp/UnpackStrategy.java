package org.eclipse.jetty.webapp;

public enum UnpackStrategy
{
    /**
     * Classic behavior: Use <code>"{workdir}/jetty-{host}-{port}-{resourceBase}-{contextPath}-{vhost}"</code>
     * as the directory name for unpacking webapps into.
     * <p>
     * Note: can be overridden by {@link WebAppContext#setTempDirectory(java.io.File)} or
     * use of {@link WebAppContext#TEMPDIR} attribute.
     */
    CLASSIC, 
    /**
     * Randomly choose a directory name off the work directory for the unpack
     * location.
     * <p>
     * Note: can be overridden by {@link WebAppContext#setTempDirectory(java.io.File)} or
     * use of {@link WebAppContext#TEMPDIR} attribute.
     */
    RANDOM,
    /**
     * Use <code>"{workdir}/jetty-{host}-{port}-{resourceBase}-{contextPath}-{vhost}-{timestamp}"</code>
     * as the directory name for unpacking webapps into.
     * <p>
     * Note: can be overridden by {@link WebAppContext#setTempDirectory(java.io.File)} or
     * use of {@link WebAppContext#TEMPDIR} attribute.
     */
    TIMESTAMP;
}
