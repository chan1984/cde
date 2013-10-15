package pt.webdetails.cdf.dd.api;

import java.io.IOException;
import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import pt.webdetails.cdf.dd.DashboardManager;
import pt.webdetails.cdf.dd.InterPluginBroker;
import pt.webdetails.cdf.dd.MetaModelManager;
import pt.webdetails.cdf.dd.editor.DashboardEditor;
import pt.webdetails.cdf.dd.model.core.writer.ThingWriteException;
import pt.webdetails.cdf.dd.model.inst.writer.cdfrunjs.dashboard.CdfRunJsDashboardWriteOptions;
import pt.webdetails.cdf.dd.model.inst.writer.cdfrunjs.dashboard.CdfRunJsDashboardWriteResult;
import pt.webdetails.cdf.dd.structure.DashboardWcdfDescriptor;
import pt.webdetails.cdf.dd.util.CdeEnvironment;
import pt.webdetails.cdf.dd.util.Utils;
import pt.webdetails.cpf.Util;
import pt.webdetails.cpf.repository.api.FileAccess;
import pt.webdetails.cpf.utils.MimeTypes;

/**
 * Created with IntelliJ IDEA. User: diogomariano Date: 07/10/13
 */
@Path( "pentaho-cdf-dd/api/renderer" )
public class RenderApi {
  private static final Log logger = LogFactory.getLog( RenderApi.class );
//  private static final String MIME_TYPE = "text/html";

  @GET
  @Path( "/getComponentDefinitions" )
  @Produces( MimeTypes.JAVASCRIPT )
  public String getComponentDefinitions( @Context HttpServletResponse response ) throws IOException {
    // Get and output the definitions
    return MetaModelManager.getInstance().getJsDefinition();
  }

  @GET
  @Path( "/getContent" )
  @Produces( MimeTypes.JAVASCRIPT )
  public String getContent( @QueryParam( MethodParams.SOLUTION ) @DefaultValue( "" ) String solution,
      @QueryParam( MethodParams.PATH ) @DefaultValue( "" ) String path,
      @QueryParam( MethodParams.FILE ) @DefaultValue( "" ) String file,
      @QueryParam( MethodParams.INFERSCHEME ) @DefaultValue( "false" ) boolean inferScheme,
      @QueryParam( MethodParams.ROOT ) @DefaultValue( "" ) String root,
      @QueryParam( MethodParams.ABSOLUTE ) @DefaultValue( "true" ) boolean absolute,
      @QueryParam( MethodParams.BYPASSCACHE ) @DefaultValue( "false" ) boolean bypassCache,
      @QueryParam( MethodParams.DEBUG ) @DefaultValue( "false" ) boolean debug, @Context HttpServletRequest request,
      @Context HttpServletResponse response ) throws IOException, ThingWriteException {

    String scheme = inferScheme ? "" : request.getScheme();
    String filePath = getWcdfRelativePath( solution, path, file );

    CdfRunJsDashboardWriteResult dashboardWrite =
        this.loadDashboard( filePath, scheme, root, absolute, bypassCache, debug );
    return dashboardWrite.getContent();
  }

  @GET
  @Path( "/getHeaders" )
  @Produces( "text/plain" )
  public String getHeaders( @QueryParam( MethodParams.SOLUTION ) @DefaultValue( "" ) String solution,
      @QueryParam( MethodParams.PATH ) @DefaultValue( "" ) String path,
      @QueryParam( MethodParams.FILE ) @DefaultValue( "" ) String file,
      @QueryParam( MethodParams.INFERSCHEME ) @DefaultValue( "false" ) boolean inferScheme,
      @QueryParam( MethodParams.ROOT ) @DefaultValue( "" ) String root,
      @QueryParam( MethodParams.ABSOLUTE ) @DefaultValue( "true" ) boolean absolute,
      @QueryParam( MethodParams.BYPASSCACHE ) @DefaultValue( "false" ) boolean bypassCache,
      @QueryParam( MethodParams.DEBUG ) @DefaultValue( "false" ) boolean debug, @Context HttpServletRequest request,
      @Context HttpServletResponse response ) throws IOException, ThingWriteException {

    String scheme = inferScheme ? "" : request.getScheme();
    String filePath = getWcdfRelativePath( solution, path, file );

    CdfRunJsDashboardWriteResult dashboardWrite =
        this.loadDashboard( filePath, scheme, root, absolute, bypassCache, debug );
    return dashboardWrite.getHeader();
  }

  @GET
  @Path( "/render" )
  @Produces( MimeTypes.HTML )
  public String render( @QueryParam( MethodParams.SOLUTION ) @DefaultValue( "" ) String solution,
      @QueryParam( MethodParams.PATH ) @DefaultValue( "" ) String path,
      @QueryParam( MethodParams.FILE ) @DefaultValue( "" ) String file,
      @QueryParam( MethodParams.INFERSCHEME ) @DefaultValue( "false" ) boolean inferScheme,
      @QueryParam( MethodParams.ROOT ) @DefaultValue( "" ) String root,
      @QueryParam( MethodParams.ABSOLUTE ) @DefaultValue( "true" ) boolean absolute,
      @QueryParam( MethodParams.BYPASSCACHE ) @DefaultValue( "false" ) boolean bypassCache,
      @QueryParam( MethodParams.DEBUG ) @DefaultValue( "false" ) boolean debug,
      @QueryParam( MethodParams.VIEWID ) @DefaultValue( "" ) String viewId    , @Context HttpServletRequest request) throws IOException {

    String scheme = inferScheme ? "" : request.getScheme();
    String filePath = getWcdfRelativePath( solution, path, file );//FIXME Util.joinPath

    // Check security
    if ( !CdeEnvironment.getUserContentAccess().hasAccess( filePath, FileAccess.EXECUTE ) ) {
      //IOUtils.write( "Access Denied or File Not Found.", response.getOutputStream() );
      return "Access Denied or File Not Found.";
    }

    try {
      long start = System.currentTimeMillis();
      logger.info( "[Timing] CDE Starting Dashboard Rendering" );
      CdfRunJsDashboardWriteResult dashboard = loadDashboard( filePath, scheme, root, absolute, bypassCache, debug );
      String result = dashboard.render(InterPluginBroker.getCdfContext(filePath, "", viewId) ); // TODO: check new interplugin call
      logger.info( "[Timing] CDE Finished Dashboard Rendering: " + Utils.ellapsedSeconds( start ) + "s" );
      return result;
    } catch ( Exception ex ) { //TODO: better error handling?
      String msg = "Could not load dashboard: " + ex.getMessage();
      logger.error( msg, ex );
      return msg;
    }
  }

  @GET
  @Path( "/edit" )
  @Produces( MimeTypes.HTML )
  public String edit(
      @QueryParam( MethodParams.SOLUTION ) @DefaultValue( "" ) String solution,
      @QueryParam( MethodParams.PATH ) @DefaultValue( "" ) String path,
      @QueryParam( MethodParams.FILE ) @DefaultValue( "" ) String file,
      @QueryParam( MethodParams.DEBUG ) @DefaultValue( "false" ) boolean debug,
      @Context HttpServletRequest request,
      @Context HttpServletResponse response ) throws Exception {

    String wcdfPath = getWcdfRelativePath( solution, path, file );

    if ( !CdeEnvironment.getUserContentAccess().hasAccess( wcdfPath, FileAccess.WRITE ) ) {
      return "Access Denied to file " + wcdfPath; //TODO: keep html?
    }

    return DashboardEditor.getEditor( wcdfPath, debug, request.getScheme() );
  }

  @GET
  @Path( "/new" )
  @Produces( MimeTypes.HTML )
  public String newDashboard( //TODO: change file to path; does new ever use this arg?
//      @QueryParam( MethodParams.SOLUTION ) @DefaultValue( "null" ) String solution,
      @QueryParam( MethodParams.PATH ) @DefaultValue( "" ) String path,
//      @QueryParam( MethodParams.FILE ) @DefaultValue( "null" ) String file,
      @QueryParam( MethodParams.DEBUG ) @DefaultValue( "false" ) boolean debug,
        @Context HttpServletRequest request,
        @Context HttpServletResponse response ) throws Exception {
    
//	  String wcdfPath = getWcdfRelativePath( solution, path, file );
    return DashboardEditor.getEditor( path, debug, request.getScheme() );
  }

  @GET
  @Path( "/listRenderers" )
  @Produces( MimeTypes.JSON )
  public String listRenderers() {
    return "{\"result\": [\"" + DashboardWcdfDescriptor.DashboardRendererType.BLUEPRINT.getType() + "\",\""
        + DashboardWcdfDescriptor.DashboardRendererType.MOBILE.getType() + "\"]}";
  }

  @GET
  @Path( "/refresh" )
  public void refresh() throws Exception {
    DashboardManager.getInstance().refreshAll();
  }

  private CdfRunJsDashboardWriteResult loadDashboard( String filePath, String scheme, String root, boolean absolute,
      boolean bypassCache, boolean debug ) throws ThingWriteException {

    CdfRunJsDashboardWriteOptions options =
        new CdfRunJsDashboardWriteOptions( absolute || !root.equals( "" ), debug, root, scheme );
    return DashboardManager.getInstance().getDashboardCdfRunJs( filePath, options, bypassCache );
  }


  private String getWcdfRelativePath( String solution, String path, String file ) {
    //TODO: change to use path instead of file
//    if ( !StringUtils.isEmpty( solution ) || !StringUtils.isEmpty( file ) ) {
//      logger.warn( "Use of solution/path/file is deprecated. Use just the path argument" );
      return Util.joinPath( solution, path, file );
//    }
//    else return path;
//    final String filePath = "/" + solution + "/" + path + "/" + file;
//    return filePath.replaceAll( "//+", "/" );
  }

  private class MethodParams {
    public static final String SOLUTION = "solution";
    public static final String PATH = "path";
    public static final String FILE = "file";
    public static final String INFERSCHEME = "inferScheme";
    public static final String ABSOLUTE = "absolute";
    public static final String ROOT = "root";
    public static final String BYPASSCACHE = "bypassCache";
    public static final String DEBUG = "debug";
    public static final String VIEWID = "viewId";
  }

}