package Macros

import com.xpn.xwiki.api.*
import com.xpn.xwiki.render.*
import com.xpn.xwiki.web.XWikiServletResponse
import org.apache.commons.io.FileUtils

public class GalleryThumbsupEngine {

    XWiki xwiki;
    Context context;
    Document doc;
    File targetDir;
    String fullNameEscaped
    // TODO adjust for the specific of the container's installation
    File baseDir = new File("webapps/ROOT/resources/pictureExports");
    String basePictureExportsURL = "/resources/pictureExports"
    String status = "IDLE"
    String debugStr = ""
    def webapp
    String webappAttributename

    ScriptXWikiServletRequest request;
    XWikiServletResponse response;

    void init(xwiki, context, doc) {
        try {
            this.xwiki = xwiki;
            this.context = context;
            this.request = context.request;
            this.response = context.response;
            this.doc = doc;
            this.fullNameEscaped = doc.fullName.replaceAll("/","__").replaceAll(" ", "-").replaceAll(":", "_")
            targetDir = new File(baseDir, fullNameEscaped)
            this.webapp = this.context.request.httpServletRequest.getServletContext()
            this.webappAttributename = "ThumbsUpProcess" + fullNameEscaped
        } catch (Exception ex) {
            debug(ex + " ")
            ex.printStackTrace()
        }
    }


    String debug(msg) {
        debugStr += msg + "\n";
        System.err.println("GalleryThumbsUpEngine: " + msg)
    }

    String dumpDebug() {
        if (request.debug || forceDebug)
            return "== DEBUG ==\n\n {{{ ${debugStr} }}}"
        else
            return "";
    }

    boolean needsActualization() {
        if( !targetDir.isDirectory()) return true;

        File outputMain = new File(new File(targetDir, "out"), "index.html")
        if(!outputMain.isFile()) return true;
        long lastModified = outputMain.lastModified()- 500 // -500 to cope for approximations of filesystems
        for(def attachment in doc.getAttachmentList()) {
            if(attachment.date.time > lastModified) return true
        }
        if(doc.date.time > lastModified) return true
        return false
    }

    String getIFrameURL() {
        return basePictureExportsURL + "/" + fullNameEscaped + "/out/index.html?version=" + doc.version
    }


    void startProduction() {
        if ( !"IDLE".equals(getProcessStatus()) ) throw new IllegalAccessException("Another process is starting or failed.")
        status = "EXPORTING"
        Thread thread = new Thread("attachmentExport") {
            public void run() {
                syncExportAttachments()
                startThumbsUp() // should finish right away but set the webappAttribute
            }
        }
        thread.start()
        webapp.setAttribute(webappAttributename,thread)
    }

    void syncExportAttachments() {
        Set attachmentNames = new HashSet()
        File srcDir = new File(new File(baseDir, fullNameEscaped), "src" )
        if(!srcDir.isDirectory())
            if(!srcDir.mkdirs()) throw new IllegalAccessException("Can't create directory " + srcDir)
        // export new or changed attachments to files'
        for(def attachment in doc.getAttachmentList()) {
            attachmentNames.add(attachment.filename)
            File targetFile= new File(srcDir, attachment.filename)
            if(!targetFile.isFile() || targetFile.lastModified() -500 < attachment.date.time) {
                debug("Saving attachment " + attachment.filename + " to " + targetFile)
                InputStream input = attachment.getContentInputStream()
                if(input==null) continue
                FileUtils.copyToFile(input, targetFile)
            }
        }
        // remove files that are not attachments
        for(File file in srcDir.listFiles()) {
            if(!attachmentNames.contains(file.name)) {
                debug("Deleting file " + file.name)
                if(!file.delete())
                    debug("File deletion failed.")
            }
        }
    }

    void startThumbsUp() {
        status = "RUNNING"
        String instructions = """./node_modules/.bin/thumbsup 
            --input "${fullNameEscaped}/src" 
            --output "${fullNameEscaped}/out" 
            --album-zip-files --title ""
            --css gallerycustom.css """
        debug("Launching " + instructions)
        Process process = instructions.execute([], baseDir)
        process.consumeProcessOutput(System.out, System.err)
        webapp.setAttribute(webappAttributename, process)
    }
    /**
     * @return * One of IDLE, RUNNING, ERROR
     */
    String getProcessStatus() {
        def att = webapp.getAttribute(webappAttributename)
        if(att == null) {
            return status = "IDLE"
        } else if(att instanceof Thread) {
            Thread t = att
            status = t.isAlive() ? "EXPORTING" : "ERROR"
        } else if(att instanceof Process) {
            Process process = att
            System.err.println("Process: " + process)
            if(process==null) return status = "IDLE"
            boolean alive = false; int exitValue = -1;
            try {exitValue = process.exitValue(); alive = false }
            catch(IllegalThreadStateException ex) { alive = true }
            if(!alive)
                if(exitValue!=0)  status == "ERROR"
                else { // exitValue==0
                    status = "IDLE"
                    webapp.setAttribute(webappAttributename, null)
                }
            else {
                // otherwise alive = true
                status = "RUNNING"
            }        }
        return status;
    }

    void stopThumbsUp() {
        def att = webapp.getAttribute(webappAttributename)
        debug("Stopping " + att)
        if(att instanceof Thread) {
            Thread t = att
            t.stop()
        } else {
            Process process = att
            process.destroy()
        }
        webapp.setAttribute(webappAttributename,null)
    }

}
