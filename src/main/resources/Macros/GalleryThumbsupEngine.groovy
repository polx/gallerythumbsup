package Macros

import com.xpn.xwiki.api.*;


public class GalleryThumbsupEngine {

    XWiki xwiki;
    Context xcontext;
    Document doc;
    File targetDir;
    String fullNameEscaped
    // TODO adjust for the specific of the container's installation
    File baseDir = new File("webapps/ROOT/resources/pictureExports");
    String basePictureExportsURL = "/resources/pictureExports"
    def request;
    def response;

    def init(xwiki, xcontext, doc) {
        this.xwiki = xwiki;
        this.xcontext = xcontext;
        this.request = xcontext.request;
        this.response = xcontext.response;
        this.doc = doc;
        this.fullNameEscaped = doc.fullName.replaceAll("/","__")
        targetDir = new File(baseDir, fullNameEscaped)
    }


    def debug(msg) {
        debugStr += msg + "\n";
    }

    String dumpDebug() {
        if (request.debug || forceDebug)
            return "== DEBUG ==\n\n {{{ ${debugStr} }}}"
        else
            return "";
    }

    boolean isEngineAvailable() {

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
        return basePictureExportsURL + "/" + fullNameEscaped + "/"
    }


}
