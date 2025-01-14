package org.qubership.graylog2.plugin.rest.resources;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.graylog2.plugin.rest.PluginRestResource;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.SchedulerException;
import org.qubership.graylog2.plugin.archiving.ArchiveInfo;
import org.qubership.graylog2.plugin.archiving.ArchivingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashSet;
import java.util.List;

/**
 * Graylog url: https://{graylog-server-url}/api/plugins/org.qubership.graylog2.plugin/{rest-api}
 */
@RequiresAuthentication
@Path("/archiving")
@Api(value = "Graylog Archiving Plugin API")
public class ArchivingResource implements PluginRestResource {

    private static final Logger log = LoggerFactory.getLogger(ArchivingResource.class);
    private static final String DEFAULT_REGION = "us-east-1";

    private final ArchivingService archivingService;

    @Inject
    public ArchivingResource(ArchivingService archivingService) {
        this.archivingService = archivingService;
    }

    @GET
    @Path("/process/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get information about archive process")
    public Response getArchiveProcessInfo(@ApiParam(name = "id") @PathParam("id") @NotEmpty String id) {
        ArchiveInfo archiveProcessInfo = archivingService.getArchiveProcessInfo(id);
        if (archiveProcessInfo == null) {
            return Response.serverError().entity("Archive process is not found!").build();
        } else {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("id", archiveProcessInfo.getId());
            jsonObject.put("startTime", archiveProcessInfo.getStartTime().toString());
            jsonObject.put("status", archiveProcessInfo.getStatus());
            if (archiveProcessInfo.getResult() != null) {
                jsonObject.put("result", archiveProcessInfo.getResult());
            }
            return Response.ok(jsonObject.toString()).build();
        }
    }

    @POST
    @Path("/unschedule/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Unschedule archiving job")
    public Response unschedule(@ApiParam(name = "id") @PathParam("id") @NotEmpty String id) {
        try {
            return Response.ok(archivingService.unschedule(id)).build();
        } catch (RuntimeException | SchedulerException exception) {
            log.error("Reason: " + exception.getMessage() + ". ", exception);
            return Response.serverError().entity("Reason: " + exception.getMessage()).build();
        }
    }

    @POST
    @Path("/schedule")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Schedule archiving job")
    public Response schedule(@NotNull String jsonData) {
        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            List<String> indices = archivingService.parametersProcessor.getList(jsonObject, "indices");
            String period = archivingService.parametersProcessor.getString(jsonObject, "period");
            String time = archivingService.parametersProcessor.getString(jsonObject, "time");
            List<String> prefixes = archivingService.parametersProcessor.getList(jsonObject, "prefixes");
            String name = archivingService.parametersProcessor.getString(jsonObject, "name");
            String stream = archivingService.parametersProcessor.getString(jsonObject, "storageId");
            if (name == null) {
                log.error("Job name is null");
                return Response.serverError().entity("Parameter 'name' is required!").build();
            } else if (period == null) {
                log.error("Period is null");
                return Response.serverError().entity("Parameter 'period' is required!").build();
            } else if (stream == null) {
                log.error("Stream is null");
                return Response.serverError().entity("Parameter 'stream' is required!").build();
            } else if (indices.isEmpty() && time == null && prefixes.isEmpty()) {
                log.error("There is no criteria for archiving");
                return Response.serverError().entity("There is no criteria for archiving!").build();
            } else {
                return Response.ok(archivingService.schedule(time, stream, name, indices, period, prefixes)).build();
            }
        } catch (JSONException exception) {
            log.error("The input json is invalid. " + "Reason: " + exception.getMessage() + ". " + "JSON=[" + jsonData + "]", exception);
            return Response.serverError().entity("Invalid json syntax. Reason: " + exception.getMessage()).build();
        } catch (RuntimeException | SchedulerException exception) {
            log.error("Reason: " + exception.getMessage() + ". ", exception);
            return Response.serverError().entity("Reason: " + exception.getMessage()).build();
        }
    }

    @POST
    @Path("/archive")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create the archive")
    public Response createArchive(@NotNull String jsonData) {
        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            List<String> indices = archivingService.parametersProcessor.getList(jsonObject, "indices");
            String time = archivingService.parametersProcessor.getString(jsonObject, "time");
            List<String> prefixes = archivingService.parametersProcessor.getList(jsonObject, "prefixes");
            List<String> indicesByParams = archivingService.getIndices(time, prefixes);
            HashSet<String> mergedIndices = new HashSet<>();
            mergedIndices.addAll(indices);
            mergedIndices.addAll(indicesByParams);
            String archiveName = archivingService.parametersProcessor.getString(jsonObject, "name");
            String storageId = archivingService.parametersProcessor.getString(jsonObject, "storageId");
            if (archiveName == null) {
                log.error("Archive name is null");
                return Response.serverError().entity("Parameter 'name' is required!").build();
            } else if (storageId == null) {
                log.error("storageId is null");
                return Response.serverError().entity("Parameter 'storageId' is required!").build();
            } else if ((mergedIndices.size() == 0) && (!time.isEmpty())) {
                log.info("Indices is empty");
                return Response.ok("Nothing found for this period").build();
            } else {
                log.info("Indices for archiving: " + mergedIndices);
                return Response.ok(archivingService.archive(storageId, archiveName, mergedIndices)).build();
            }
        } catch (JSONException exception) {
            log.error("The input json is invalid. " + "Reason: " + exception.getMessage() + ". " + "JSON=[" + jsonData + "]", exception);
            return Response.serverError().entity("Invalid json syntax. Reason: " + exception.getMessage()).build();
        } catch (RuntimeException exception) {
            log.error("Reason: " + exception.getMessage() + ". ", exception);
            return Response.serverError().entity("Reason: " + exception.getMessage()).build();
        }
    }

    @GET
    @Path("/archive/{archiveName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Get information about the archive")
    public Response getArchive(@ApiParam(name = "archiveName") @PathParam("archiveName") @NotEmpty String archiveName) {
        try {
            return Response.ok(archivingService.readInfoFile(archiveName)).build();
        } catch (Exception e) {
            log.error("An error has occurred during getting archive info. " + "Reason: " + e.getMessage() + ". ", e);
            return Response.serverError().entity("Reason: " + e.getMessage()).build();
        }
    }

    @POST
    @Path("/restore/{archiveName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Restore the archive")
    public Response restoreArchive(@ApiParam(name = "archiveName") @PathParam("archiveName") @NotEmpty String archiveName, @NotNull String jsonData) {
        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            String stream = archivingService.parametersProcessor.getString(jsonObject, "storageId");
            if (stream == null) {
                log.error("storageId name is null");
                return Response.serverError().entity("Parameter 'storageId' is required!").build();
            } else return Response.ok(archivingService.restore(stream, archiveName)).build();
        } catch (Exception e) {
            log.error("An error has occurred during restore. " + "Reason: " + e.getMessage() + ". ", e);
            return Response.serverError().entity("Reason: " + e.getMessage()).build();
        }
    }

    @DELETE
    @Path("/{stream}/{archiveName}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Delete archive")
    public Response deleteArchive(@ApiParam(name = "stream") @PathParam("stream") @NotEmpty String stream, @ApiParam(name = "archiveName") @PathParam("archiveName") @NotEmpty String archiveName) {
        try {
            return Response.ok(archivingService.delete(stream, archiveName)).build();
        } catch (Exception e) {
            log.error("An error has occurred during deleting archive. " + "Reason: " + e.getMessage() + ". ", e);
            return Response.serverError().entity("Reason: " + e.getMessage()).build();
        }
    }

    @POST
    @Path("/settings/reload")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Reload directories.json file")
    public Response reloadSettings() {
        try {
            return Response.ok(archivingService.readDirectoriesFile()).build();
        } catch (RuntimeException exception) {
            log.error("Reason: " + exception.getMessage() + ". ", exception);
            return Response.serverError().entity("Reason: " + exception.getMessage()).build();
        }
    }

    @POST
    @Path("/settings/s3")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Set parameters for snapshot directories")
    public Response s3settings(@NotNull String jsonData) {
        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            String storageId = archivingService.parametersProcessor.getString(jsonObject, "storageId");
            String bucket = archivingService.parametersProcessor.getString(jsonObject, "bucket");
            String region = archivingService.parametersProcessor.getString(jsonObject, "region");
            String endpoint = archivingService.parametersProcessor.getString(jsonObject, "endpoint");
            String roleArn = archivingService.parametersProcessor.getString(jsonObject, "roleARN");
            if (storageId == null) {
                log.error("Parameter storageId is null");
                return Response.serverError().entity("Parameter 'storageId' is required!").build();
            } else if (bucket == null) {
                log.error("Parameter bucket is null");
                return Response.serverError().entity("Parameter 'bucket' is required!").build();
            } else if (region == null) {
                log.info("Parameter region is null. Set to default value");
                region = DEFAULT_REGION;
            }
            log.info("Register s3 repository:");
            return Response.ok(archivingService.addS3Settings(storageId, endpoint, bucket, region, roleArn)).build();
        } catch (JSONException exception) {
            log.error("The input json is invalid. " + "Reason: " + exception.getMessage() + ". " + "JSON=[" + jsonData + "]", exception);
            return Response.serverError().entity("Invalid json syntax. Reason: " + exception.getMessage()).build();
        } catch (RuntimeException exception) {
            log.error("Reason: " + exception.getMessage() + ". ", exception);
            return Response.serverError().entity("Reason: " + exception.getMessage()).build();
        }
    }

    @POST
    @Path("/settings/fs")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Set parameters for snapshot directories")
    public Response settings(@NotNull String jsonData) {
        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            String storageId = archivingService.parametersProcessor.getString(jsonObject, "storageId");
            String snapshotDir = archivingService.parametersProcessor.getString(jsonObject, "snapshotDir");
            if (storageId == null) {
                log.error("Parameter storageId is null");
                return Response.serverError().entity("Parameter 'storageId' is required!").build();
            } else {
                return Response.ok(archivingService.addFSSettings(storageId, snapshotDir)).build();
            }
        } catch (JSONException exception) {
            log.error("The input json is invalid. " + "Reason: " + exception.getMessage() + ". " + "JSON=[" + jsonData + "]", exception);
            return Response.serverError().entity("Invalid json syntax. Reason: " + exception.getMessage()).build();
        } catch (RuntimeException exception) {
            log.error("Reason: " + exception.getMessage() + ". ", exception);
            return Response.serverError().entity("Reason: " + exception.getMessage()).build();
        }
    }

}