# Graylog Archiving Plugin

Plugin for archiving ElasticSearch indices.

* [Graylog Archiving Plugin](#graylog-archiving-plugin)
  * [Prerequisites](#prerequisites)
    * [Common](#common)
    * [Volumes](#volumes)
    * [S3-storage](#s3-storage)
    * [AWS prerequisites](#aws-prerequisites)
  * [Usage](#usage)
  * [Examples](#examples)
    * [Register FS directory](#register-fs-directory)
    * [Register S3 directory](#register-s3-directory)
    * [Reload config file](#reload-config-file)
    * [Create an archive with specific indices](#create-an-archive-with-specific-indices)
    * [Create an archive with indices by prefix](#create-an-archive-with-indices-by-prefix)
    * [Create an archive with indices by time](#create-an-archive-with-indices-by-time)
    * [Create an archive with multiple parameters](#create-an-archive-with-multiple-parameters)
    * [Get archiving process information](#get-archiving-process-information)
    * [Get archive information](#get-archive-information)
    * [Restore archive](#restore-archive)
    * [Get restoring process information](#get-restoring-process-information)
    * [Delete archive](#delete-archive)
    * [Get deleting process information](#get-deleting-process-information)
    * [Schedule job](#schedule-job)
    * [Unscheduled job](#unscheduled-job)
  * [Build](#build)
  * [Release](#release)
    * [Before release](#before-release)

## Prerequisites

### Common

For the successful installation you should install `acl` and `unzip` on your VM.

The graylog-archiving-plugin uses `Restored index set` to restore logs with `Restored logs` stream.

> **Note:**
>
> Only users who have `AuditViewer` role can see restored logs.

### Volumes

The graylog-archiving-plugin stores zip-archives in ElasticSearch/OpenSearch volume "archives" mounted by path
from the `GRAYLOG_SNAPSHOT_DIRECTORY` environment variable.
This volume should be created as a shared file system repository ("type": "fs").

In order to register the shared file system repository it is necessary to mount the same shared filesystem
to the same location on all master and data nodes.
This location (or one of its parent directories) must be registered in the path.repo setting on all master
and data nodes.

### S3-storage

For using S3 storage you should manually add connection parameters to the elasticsearch.keystore with the next commands (the same for the Opensearch):

```bash
docker exec -it graylog_elasticsearch_1 sh
echo accessKey| bin/elasticsearch-keystore add --stdin --force s3.client.default.access_key 
echo secretKey| bin/elasticsearch-keystore add --stdin --force s3.client.default.secret_key 
echo https://s3.amazonaws.com| bin/elasticsearch-keystore add --stdin --force s3.client.default.endpoint 
exit
```

Where `accessKey` and `secretKey` - parameters for IAM user in the S3.

Command for applying settings:

```bash
curl -X POST localhost:9200/_nodes/reload_secure_settings
```

### AWS prerequisites

For correct work archiving plugin in AWS you should create user with necessary grants.

Note: in case of you have 403 error during usage plugin check that your user has necessary grants for managing
snapshots like it described in the
[Official doc](https://repost.aws/knowledge-center/opensearch-manual-snapshot-error)

## Usage

There is config file for mapping streams and backup directories: `/var/lib/graylog/config/directories.json`

There is an example of the content:

```bash
[root@vm ~]# cat /var/lib/graylog/config/directories.json
{
  "graylog": "/usr/share/elasticsearch/snapshots/graylog/graylog",
  "gray_audit": "gray_audit"
}
```

It means that archives for `graylog_*` indices will be stored in `/usr/share/elasticsearch/snapshots/graylog/graylog`
directory and for `gray_audit_*` indices in the S3 bucket `gray_audit`.
If prefix for stream can't be found in the directories.conf file it automatically been created as file-system storage.

For adding settings you can manually edit `direcotries.conf` file with reload conf via REST-api or
use special REST-api for add new settings.

The graylog-archiving-plugin provides next REST-api access via URL: `https://{graylog-server-url}/api/plugins/org.qubership.graylog2.plugin/archiving/{rest-api}`:

| METHOD | PATH                       | RESULT                               |
|--------|----------------------------|--------------------------------------|
| POST   | /settings/fs               | Status of registration fs-repository |
| POST   | /settings/s3               | Status of registration s3-repository |
| POST   | /settings/reload           | Map with registered repositories     |
| POST   | /archive                   | Uuid of created archive process      |
| GET    | /process/{uuid}            | Status of archive/restore process    |
| GET    | /archive/{archiveName}     | Detailed info about archive          |
| POST   | /restore/{archiveName}     | Uuid of created restore process      |
| DELETE | /{storageId}/{archiveName} | Uuid of created delete process       |
| POST   | /schedule                  | Status of scheduling job             |
| POST   | /unschedule/{name}         | Status of unscheduling job           |

> **Note:**
>
> You should be authorized and use header `X-Requested-By = Graylog Api Browser`.

There are next parameters for the `FS settings`:

| NAME        | TYPE   | REQUIRED | DESCRIPTION                                                                                                 |
|-------------|--------|----------|-------------------------------------------------------------------------------------------------------------|
| storageId   | String | Yes      | Storage identifier for archiving stream. It must be like index-prefix for example `graylog` or `gray_audit` |
| snapshotDir | String | No       | Snapshot directory for file-system storage. Default value `/usr/share/elasticsearch/snapshots/graylog/`     |

> **Note:**
>
> This command can't be performed in case of AWS-deploy with S3 storage


There are next parameters for the `S3 settings`:

| NAME       | TYPE   | REQUIRED | DESCRIPTION                                         |
|------------|--------|----------|----------------------------------------------------|
| storageId  | String | Yes      | Storage identifier for archiving stream. It must be like index-prefix for example `graylog` or `gray_audit` |
| bucket     | String | Yes      | Bucket in the S3 associated with stream from prefix                                                         |
| region     | String | No       | Region in the S3, by default `us-east-1`                                                                    |
| endpoint   | String | Yes      | URL for S3-service                                                                                          |
| roleARN    | String | No       | Role ARN for the user in AWS. Used only in case of AWS-deploy with S3 storage                               |

There are next parameters for the `archive` procedure:

| NAME      | TYPE   | REQUIRED | DESCRIPTION                                                                                                  |
|-----------|--------|----------|--------------------------------------------------------------------------------------------------------------|
| name      | String | Yes      | Name of archive                                                                                              |
| indices   | List   | No       | Indices names for archiving                                                                                  |
| time      | String | No       | Timeunit string for archiving indices created before fixed time. Supported values: m, h, d. Example: `1d12h` |
| prefixes  | List   | No       | Indices prefixes for archiving, for example `["graylog_", "gray_audit"]`                                     |
| storageId | String | Yes      | Storage identifier for archiving stream, for example `"graylog"` to manage directory for saving              |

> **Note:**
>
> You should set at least one of the not-required parameters

There are next parameters for the `schedule` procedure:

| NAME      | TYPE   | REQUIRED | DESCRIPTION                                                                                                  |
|-----------|--------|----------|--------------------------------------------------------------------------------------------------------------|
| name      | String | Yes      | Name for scheduling job. Also uses by prefix for archive name. It looks like `name_yyyyMMdd_HHmmss`          |
| indices   | List   | No       | Indices names for archiving                                                                                  |
| time      | String | No       | Timeunit string for archiving indices created before fixed time. Supported values: m, h, d. Example: `1d12h` |
| prefixes  | List   | No       | Indices prefixes for archiving, for example `["graylog_", "gray_audit"]`                                     |
| period    | String | Yes      | Cron string for periodically running archiving procedure. Example: `0 0 0/1 * * ?`                           |
| storageId | String | Yes      | Storage identifier for archiving stream, for example `"graylog"` to manage directory for saving              |

More about CRON format: [java-cron-expression](https://www.javatpoint.com/java-cron-expression)

## Examples

### Register FS directory

This operation create/override directory for stream on filesystem.

Request:

```bash
POST https://x.x.x.x/api/plugins/org.qubership.graylog2.plugin/archiving/settings/fs

{
    "storageId": "gray_audit"
}
```

Response:

```bash
{
    "acknowledged": true
}
```

### Register S3 directory

This operation create/override directory for stream on S3.

Request:

```bash
POST https://x.x.x.x/api/plugins/org.qubership.graylog2.plugin/archiving/settings/s3

{
    "storageId": "graylog",
    "bucket": "graylog",
    "region": "us-east-1",
    "endpoint": "http://x.x.x.x:30900"
}
```

Response:

```bash
{
    "acknowledged": true
}
```

### Reload config file

This operation create/override directory for stream on S3.

Request:

```bash
POST https://x.x.x.x/api/plugins/org.qubership.graylog2.plugin/archiving/settings/reload
```

Response:

```bash
{
    "graylog": "/usr/share/elasticsearch/snapshots/graylog/graylog",
    "gray_audit": "/usr/share/elasticsearch/snapshots/graylog/gray_audit"
}
```

### Create an archive with specific indices

This operation archive only indices from parameter `indices`.

Request:

```bash
POST https://x.x.x.x/api/plugins/org.qubership.graylog2.plugin/archiving/archive

{
    "name": "test001",
    "indices": ["graylog_1", "graylog_2"],
    "storageId": "graylog"
}
```

Response:

```bash
1e795c81-5628-4d1e-888b-b92925f59116
```

### Create an archive with indices by prefix

This operation archives all indices whose names start from list-parameter `prefixes`.

Request:

```bash
POST https://x.x.x.x/api/plugins/org.qubership.graylog2.plugin/archiving/archive

{
    "name": "test001",
    "prefixes": ["graylog_"],
    "storageId": "graylog"
}
```

Response:

```bash
3d521a49-5016-8c0e-180a-b34335f97655
```

### Create an archive with indices by time

This operation archive all indices created earlier than the value from parameter `time`.

Request:

```bash
POST https://x.x.x.x/api/plugins/org.qubership.graylog2.plugin/archiving/archive

{
    "name": "test001",
    "time": "1d",
    "storageId": "graylog"
}
```

Response:

```bash
2c665a20-3474-2d1a-369b-b76552f97655
```

### Create an archive with multiple parameters

This operation archive all indices whose names start from list-parameter `prefixes` created earlier than the value
from parameter `time`.

Request:

```bash
POST https://x.x.x.x/api/plugins/org.qubership.graylog2.plugin/archiving/archive

{
    "name": "test001",
    "prefixes": ["graylog_"],
    "time": "1d",
    "storageId": "graylog"
}
```

Response:

```bash
2c665a20-3474-2d1a-369b-b76552f97655
```

### Get archiving process information

Request:

```bash
GET https://x.x.x.x/api/plugins/org.qubership.graylog2.plugin/archiving/process/1e795c81-5628-4d1e-888b-b92925f59116
```

Response:

```json
{
    "startTime": "Mon Dec 21 15:25:08 UTC 2020",
    "id": "1e795c81-5628-4d1e-888b-b92925f59116",
    "status": "Starting archive procedure"
}
```

```json
{
    "startTime": "Mon Dec 21 15:25:09 UTC 2020",
    "id": "1e795c81-5628-4d1e-888b-b92925f59116",
    "status": "Loading data from Elasticsearch"
}
```

```json
{
    "result": "{\"snapshots\":[{\"snapshot\":\"test001\",\"uuid\":\"80mNbGcDTSyKa9LEEwIDhg\",\"version_id\":6081299,\"version\":\"6.8.12\",\"indices\":[\"graylog_86\"],\"include_global_state\":true,\"state\":\"SUCCESS\",\"start_time\":\"2020-12-21T15:25:08.313Z\",\"start_time_in_millis\":1608564308313,\"end_time\":\"2020-12-21T15:25:40.675Z\",\"end_time_in_millis\":1608564340675,\"duration_in_millis\":32362,\"failures\":[],\"shards\":{\"total\":4,\"failed\":0,\"successful\":4}}]}",
    "startTime": "Mon Dec 21 15:25:08 UTC 2020",
    "id": "1e795c81-5628-4d1e-888b-b92925f59116",
    "status": "Success"
}
```

### Get archive information

Request:

```bash
GET https://x.x.x.x/api/plugins/org.qubership.graylog2.plugin/archiving/archive/test001
```

Response:

```json
{
    "snapshots": [
        {
            "snapshot": "test001",
            "repository": "archives",
            "uuid": "YKTKs3dJT9qD0QuUfRyhzw",
            "state": "SUCCESS",
            "include_global_state": true,
            "shards_stats": {
                "initializing": 0,
                "started": 0,
                "finalizing": 0,
                "done": 4,
                "failed": 0,
                "total": 4
            },
            "stats": {
                "incremental": {
                    "file_count": 0,
                    "size_in_bytes": 0
                },
                "total": {
                    "file_count": 60,
                    "size_in_bytes": 830777647
                },
                "start_time_in_millis": 1608557913394,
                "time_in_millis": 117,
                "number_of_files": 0,
                "processed_files": 0,
                "total_size_in_bytes": 0,
                "processed_size_in_bytes": 0
            },
            "indices": {
                "graylog_84": {
                    "shards_stats": {
                        "initializing": 0,
                        "started": 0,
                        "finalizing": 0,
                        "done": 4,
                        "failed": 0,
                        "total": 4
                    },
                    "stats": {
                        "incremental": {
                            "file_count": 0,
                            "size_in_bytes": 0
                        },
                        "total": {
                            "file_count": 60,
                            "size_in_bytes": 830777647
                        },
                        "start_time_in_millis": 1608557913394,
                        "time_in_millis": 117,
                        "number_of_files": 0,
                        "processed_files": 0,
                        "total_size_in_bytes": 0,
                        "processed_size_in_bytes": 0
                    },
                    "shards": {
                        "0": {
                            "stage": "DONE",
                            "stats": {
                                "incremental": {
                                    "file_count": 0,
                                    "size_in_bytes": 0
                                },
                                "total": {
                                    "file_count": 15,
                                    "size_in_bytes": 207158834
                                },
                                "start_time_in_millis": 1608557913466,
                                "time_in_millis": 7,
                                "number_of_files": 0,
                                "processed_files": 0,
                                "total_size_in_bytes": 0,
                                "processed_size_in_bytes": 0
                            }
                        },
                        "1": {
                            "stage": "DONE",
                            "stats": {
                                "incremental": {
                                    "file_count": 0,
                                    "size_in_bytes": 0
                                },
                                "total": {
                                    "file_count": 15,
                                    "size_in_bytes": 208091230
                                },
                                "start_time_in_millis": 1608557913500,
                                "time_in_millis": 11,
                                "number_of_files": 0,
                                "processed_files": 0,
                                "total_size_in_bytes": 0,
                                "processed_size_in_bytes": 0
                            }
                        },
                        "2": {
                            "stage": "DONE",
                            "stats": {
                                "incremental": {
                                    "file_count": 0,
                                    "size_in_bytes": 0
                                },
                                "total": {
                                    "file_count": 15,
                                    "size_in_bytes": 207829113
                                },
                                "start_time_in_millis": 1608557913394,
                                "time_in_millis": 6,
                                "number_of_files": 0,
                                "processed_files": 0,
                                "total_size_in_bytes": 0,
                                "processed_size_in_bytes": 0
                            }
                        },
                        "3": {
                            "stage": "DONE",
                            "stats": {
                                "incremental": {
                                    "file_count": 0,
                                    "size_in_bytes": 0
                                },
                                "total": {
                                    "file_count": 15,
                                    "size_in_bytes": 207698470
                                },
                                "start_time_in_millis": 1608557913422,
                                "time_in_millis": 26,
                                "number_of_files": 0,
                                "processed_files": 0,
                                "total_size_in_bytes": 0,
                                "processed_size_in_bytes": 0
                            }
                        }
                    }
                }
            }
        }
    ]
}
```

### Restore archive

Request:

```bash
POST https://x.x.x.x/api/plugins/org.qubership.graylog2.plugin/archiving/restore/test001

{
    "storageId": "graylog"
}
```

Response:

```bash
8597d4a1-f9c8-4410-8796-51aa90b1312b
```

### Get restoring process information

Request:

```bash
GET https://x.x.x.x/api/plugins/org.qubership.graylog2.plugin/archiving/process/8597d4a1-f9c8-4410-8796-51aa90b1312b
```

Response:

```json
{
    "startTime": "Mon Dec 21 15:25:08 UTC 2020",
    "id": "8597d4a1-f9c8-4410-8796-51aa90b1312b",
    "status": "Starting restore procedure"
}
```

```json
{
    "startTime": "Mon Dec 21 15:25:10 UTC 2020",
    "id": "8597d4a1-f9c8-4410-8796-51aa90b1312b",
    "status": "Restoring: graylog_0"
}
```

```json
{
    "result": "{\"graylog_0\":\"{\\\"accepted\\\":true}\",\"graylog_2\":\"{\\\"accepted\\\":true}\"}",
    "startTime": "Thu Dec 24 06:26:41 UTC 2020",
    "id": "8597d4a1-f9c8-4410-8796-51aa90b1312b",
    "status": "Success"
}
```

### Delete archive

Request:

```bash
DELETE https://x.x.x.x/api/plugins/org.qubership.graylog2.plugin/archiving/graylog/test001
```

Response:

```bash
5004efbc-b382-459f-948d-c6a35f442120
```

### Get deleting process information

Request:

```bash
GET https://x.x.x.x/api/plugins/org.qubership.graylog2.plugin/archiving/process/5004efbc-b382-459f-948d-c6a35f442120
```

Response:

```json
{
  "startTime": "Fri Mar 17 06:28:22 UTC 2023",
  "id": "fc3e8fae-aa1b-487d-88f5-535d718113e4",
  "status": "Waiting for deletion data from elasticsearch"
}
```

```json
{
  "startTime": "Fri Mar 17 06:28:22 UTC 2023",
  "id": "fc3e8fae-aa1b-487d-88f5-535d718113e4",
  "status": "Waiting for deletion data from volume"
}
```

```json
{
  "result": "Success",
  "startTime": "Fri Mar 17 06:28:23 UTC 2023",
  "id": "fc3e8fae-aa1b-487d-88f5-535d718113e4",
  "status": "Success"
}
```

### Schedule job

Request:

```bash
POST https://x.x.x.x/api/plugins/org.qubership.graylog2.plugin/archiving/schedule
```

```json
{
    "name": "test",
    "prefixes":  ["graylog_"],
    "time": "12h",
    "period": "0 0 0 0/1 * ?",
    "storageId": "graylog"
}
```

Response:

```bash
true
```

### Unscheduled job

Request:

```bash
POST https://x.x.x.x/api/plugins/org.qubership.graylog2.plugin/archiving/unschedule/test
```

Response:

```bash
true
```

## Build

To run the build for this plugin need the:

* JDK >= 11.x
* Maven >= 3.8.x

Build can be run using the command:

```bash
mvn clean install
```

## Release

### Before release

Add builds in the master branch are SNAPSHOT builds. They can be promoted.

So before creating a tag and running a build for promotion you need to change versions and remote SNAPSHOT word.
You can do it by using the command:

```bash
mvn versions:set -DnewVersion="<version>"
```

for example:

```bash
mvn versions:set -DnewVersion="0.2.0"
```

After it, you can verify that the plugin working as expected and create a new tag with the version.
Plugin's version in `pom.xml` and tag should have the same version.
