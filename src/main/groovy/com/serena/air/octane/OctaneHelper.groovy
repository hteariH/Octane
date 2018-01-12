package com.serena.air.octane

import com.serena.air.StepFailedException
import com.serena.air.http.HttpBaseClient
import com.serena.air.http.HttpResponse
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.http.HttpEntity
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.conn.HttpHostConnectException
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.BasicCookieStore
import groovy.xml.StreamingMarkupBuilder

import static com.serena.air.octane.FailMode.*

class OctaneHelper extends HttpBaseClient {

    AuthMethod authMethod = AuthMethod.API_KEY
    long sharedSpaceId = 101
    long workspaceId = 102
    boolean debug = false

    static final String SESSION_URL = '/authentication/sign_in'

    OctaneHelper(String serverUrl, String username, String password) {
        super(serverUrl, username, password)
    }

    @Override
    protected String getFullServerUrl(String serverUrl) {
         return serverUrl
    }

    /**
     * Login to Octane with either API Key or User/Password authentication
     */
    def login() {
        def jsonBody
        if (this.authMethod == AuthMethod.API_KEY) {
            // API Key authentication
            if (debug) println "Using Octane API Key authentication"
            jsonBody = JsonOutput.toJson([user: username, password: password])
        } else {
            // User/Password authentication
            if (debug) println "Using Octane username / password authentication"
            jsonBody = JsonOutput.toJson([client_id: username, client_secret: password])
        }

        BasicCookieStore cookieStore = new BasicCookieStore()
        defaultContext.cookieStore = cookieStore
        defaultContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore)

        HttpResponse response = execPost(SESSION_URL, jsonBody)
        checkStatusCode(response.code)
    }

    def createWorkItem(EntityType eType, String eName, String eDescription, String eParentType, long eParentId,
                       String eProductAreaIds, long eReleaseId, String eSeverityId) {

        def entityUrl = getEntityUrl(eType, 0)

        List<JsonBuilder> prodListJson = new ArrayList<>()
        if (eProductAreaIds) {
            List<String> prodAreaList = csvToList(eProductAreaIds)
            for (def prodArea: prodAreaList) {
                JsonBuilder tmpJson = new JsonBuilder()
                tmpJson {
                    type "product_area"
                    id prodArea
                }
                prodListJson.add(tmpJson.getContent())
            }
        }

        JsonBuilder eJson = new JsonBuilder()
        eJson {
            name eName

            if (eDescription) {
                description eDescription
            }

            if (eParentId) {
                parent(
                    type: eParentType,
                    id: Long.toString(eParentId)
                )
            }

            if (eProductAreaIds) {
                product_areas(
                    data: prodListJson
                )
            }

            if (eReleaseId) {
                release(
                    type: "release",
                    id: Long.toString(eReleaseId)
                )
            }

            if (eSeverityId) {
                severity(
                    type: "list_node",
                    id: eSeverityId
                )
            }

        }

        if (debug) println "Creating ${eType} using JSON:\n" + eJson.toPrettyString()

        JsonBuilder body = new JsonBuilder(data: [eJson.getContent()])

        HttpResponse response = execPost(entityUrl, body)
        checkStatusCode(response.code)

        if (response.code != 201) {
            if (response.code == 409) {
                def json = new JsonSlurper().parseText(response.body)
                def jsonErrors = json?.errors
                def errString = ""
                for (def jsonErr: jsonErrors) {
                    errString += jsonErr.description_translated
                }
                if (jsonErrors) {
                    throw new StepFailedException(errString)
                }
            }

            throw new StepFailedException('Error creating work item.')
        } else {
            def json = new JsonSlurper().parseText(response.body)
            return json?.data[0]
        }
    }

    def updateWorkItems(List<String> eIds, String ePhaseId, String eCommentText, long eCommentAuthorId,
                        FailMode failMode) {
        int failures = 0

        eIds.each { String eId ->
            EntityType eType = getWorkItemType(Long.parseLong(eId))

            if (eType == EntityType.UNKNOWN || eType == EntityType.NOT_FOUND) {
                if (failMode == FAIL_FAST) {
                    throw new StepFailedException("Not found or unsupported type for work item id: $eId")
                }

                println "Skipping work item $eId: not found or type unsupported."
                failures++
                return
            }

            //TODO: check if phase is valid

            def entityUrl = getEntityUrl(eType, Long.parseLong(eId))

            JsonBuilder eJson = new JsonBuilder()
            eJson {
                phase(
                    type: "phase",
                    id: ePhaseId
                )
            }

            println "Updating ${eType}: ${eId}."
            if (debug) println "Using URL: ${entityUrl} and JSON:\n" + eJson.toPrettyString()

            JsonBuilder body = new JsonBuilder(eJson.getContent())

            HttpResponse response = execPut(entityUrl, body)

            switch (response.code) {
                case 204:
                    break

                case 404:
                    if (failMode == FAIL_FAST) {
                        throw new StepFailedException("Could not find work item: $eId")
                    }

                    println "Skipping work item $eId: not found."
                    failures++
                    break

                case 400:
                    if (failMode == FAIL_FAST) {
                        throw new StepFailedException("Could not update work item: $eId to status $ePhaseId")
                    }

                    println "Skipping work item $eId: update to status $ePhaseId failed."
                    failures++
                    break

                default:
                    checkStatusCode(response.code)
            }

            if (eCommentText) {
                createComment(eType, Long.parseLong(eId), eCommentText, eCommentAuthorId)
            }
        }

        if (failMode == FAIL_ON_NO_UPDATES && eIds.size() == failures) {
            throw new StepFailedException('Could not update any entities!')
        }

        if (failures) {
            println "Failed to update $failures entities."
        }
    }

    def commentWorkItems(List<String> eIds, String eCommentText, long eCommentAuthorId,
                         FailMode failMode) {
        int failures = 0

        eIds.each { String eId ->
            EntityType eType = getWorkItemType(Long.parseLong(eId))

            if (eType == EntityType.UNKNOWN || eType == EntityType.NOT_FOUND) {
                if (failMode == FAIL_FAST) {
                    throw new StepFailedException("Not found or unsupported type for work item id: $eId")
                }

                println "Skipping work item $eId: specified work item not found or type unsupported."
                failures++
                return
            }

            try {
                createComment(eType, Long.parseLong(eId), eCommentText, eCommentAuthorId)
            } catch (StepFailedException ex) {
                if (failMode == FAIL_FAST) {
                    throw new StepFailedException("Could not create comment for work item: $eId")
                }

                println "Skipping work item $eId: comment could not be created."
                failures++
            }
        }

        if (failMode == FAIL_ON_NO_UPDATES && eIds.size() == failures) {
            throw new StepFailedException('Could not update any work items!')
        }

        if (failures) {
            println "Failed to update $failures work items."
        }
    }

    def checkWorkItemStatus(List<String> eIds, String expectedStatus, FailMode failMode) {
        expectedStatus = expectedStatus.toLowerCase()

        int failures = 0

        eIds.each { String eId ->
            HttpResponse response = execGet(getBaseUrl() + "/work_items/" + eId)

            if (response.code == 404) {
                if (failMode == FAIL_FAST) {
                    throw new StepFailedException("Could not find work item: $eId")
                }

                println "Skipping work item $eId: Specified work item not found."
                failures++
                return
            } else {
                checkStatusCode(response.code)
            }

            def json = new JsonSlurper().parseText(response.body)
            def jsonPhase = json?.phase
            String actualStatus = jsonPhase.id

            println "Status of ${eId} is ${actualStatus}"

            if (expectedStatus != actualStatus.toLowerCase()) {
                if (failMode == FAIL_FAST) {
                    throw new StepFailedException("Not expected status for work item $eId: actual status is $actualStatus.")
                }

                println "Not expected status for work item $eId: actual status is $actualStatus."
                failures++
            }
        }

        if (failMode == FAIL_ON_ANY_FAILURE && failures) {
            throw new StepFailedException('Error retrieving status!')
        }

        if (failMode == FAIL_ON_NO_UPDATES && eIds.size() == failures) {
            throw new StepFailedException('All Failed!')
        }

    }

    def buildWorkItemReport(List<String> eIds) {
        def builder = new StreamingMarkupBuilder()
        builder.encoding = 'UTF-8'

        def eXml = builder.bind {
            work_items() {
                for (def eId : eIds) {
                    def eJson = getEntityJson(eId)

                    if (eJson == null) {
                        println "Skipping work item $eId: Specified work item not found."
                    } else {
                        work_item(id: eJson.id, 'issue-tracker': 'Octane') {
                            name(eJson.name)
                            description(eJson.description)
                            type(eJson.subtype)
                            phase(eJson.phase.id)
                        }
                    }
                }
            }
        }

        return eXml.toString()
    }

    def createComment(EntityType eType, long eId, String eComment, long eAuthorId) {
        def eTypeName
        switch(eType) {
            case EntityType.DEFECT: eTypeName = "defect";
                break;
            case EntityType.STORY: eTypeName = "story";
                break;
            default:
                throw new StepFailedException("Not found or unsupported type for work item id: $eId")
        }

        JsonBuilder cJson = new JsonBuilder()
        cJson {
            if (eAuthorId) {
                author(
                    type: "workspace_user",
                    id: Long.toString(eAuthorId)
                )
            }

            text eComment

            owner_work_item(
                type: eTypeName,
                id: Long.toString(eId)
            )

        }

        def eCommentUrl = getBaseUrl() + "/comments"
        println "Creating comment on ${eType}: ${eId}."
        if (debug) println "Using URL: ${eCommentUrl} and JSON:\n" + cJson.toPrettyString()

        JsonBuilder body = new JsonBuilder(data: [cJson.getContent()])

        HttpResponse response = execPost(eCommentUrl, body)
        checkStatusCode(response.code)

        if (response.code != 201) {
            if (response.code == 409) {
                def json = new JsonSlurper().parseText(response.body)
                def jsonErrors = json?.errors
                def errString = ""
                for (def jsonErr: jsonErrors) {
                    errString += jsonErr.description_translated
                }
                if (jsonErrors) {
                    throw new StepFailedException(errString)
                }
            }

            throw new StepFailedException('Comment has not been created.')
        } else {
            def json = new JsonSlurper().parseText(response.body)
            return json?.data[0]
        }
    }

    def EntityType getWorkItemType(long eId) {
        HttpResponse response = execGet(getBaseUrl() + "/work_items/" + Long.toString(eId))

        if (response.code == 404) {
            return EntityType.NOT_FOUND
        }

        checkStatusCode(response.code)

        def json = new JsonSlurper().parseText(response.body)

        switch (json?.subtype) {
            case "defect":  return EntityType.DEFECT
            case "story": return EntityType.STORY
            default: return EntityType.UNKNOWN
        }
    }

    //

    static List<String> csvToList(String csv) {
        if (!csv.replaceAll(',', '').trim()) {
            throw new StepFailedException('List of IDs is empty!')
        }

        def result = []

        csv.split(',').each {
            def trimmedValue = it.trim()

            if (trimmedValue) {
                result << trimmedValue
            }
        }

        return result
    }

    static boolean isNotEmpty(String str) {
        return (str != null) && !(str.trim().isEmpty());
    }

    static boolean isEmpty(String str) {
        return (str == null) || str.trim().isEmpty();
    }

    //
    // private methods
    //

    private def getBaseUrl() {
        return "/api/shared_spaces/" + Long.toString(sharedSpaceId) +
                "/workspaces/" + Long.toString(workspaceId)
    }

    private def getEntityUrl(EntityType eType, long eId) {
        def entityUrl = getBaseUrl()
        switch (eType) {
            case EntityType.DEFECT: entityUrl += "/defects"
                break;
            case EntityType.STORY: entityUrl += "/stories"
                break;
            default:
                throw new StepFailedException("Unsupported type: " + eType)
        }
        if (eId != null && eId > 0) entityUrl += ("/" + Long.toString(eId))
        return entityUrl
    }

    private def getEntityJson(def eId) {
        HttpResponse response = execGet(getBaseUrl() + "/work_items/" + eId)

        if (response.code == 404) {
            return null
        }

        checkStatusCode(response.code)

        return new JsonSlurper().parseText(response.body)
    }

    private HttpResponse execMethod(def method) {
        try {
            return exec(method)
        } catch (UnknownHostException e) {
            throw new StepFailedException("Unknown host: ${e.message}")
        } catch (HttpHostConnectException ignore) {
            throw new StepFailedException('Connection refused!')
        }
    }

    private HttpResponse execGet(def url) {
        HttpGet method = new HttpGet(getUriBuilder(url.toString()).build())
        return execMethod(method)
    }

    private HttpResponse execPost(def url, def json) {
        HttpPost method = new HttpPost(getUriBuilder(url.toString()).build())
        // needed for Comments REST API
        method.addHeader("HPECLIENTTYPE", "HPE_REST_API_TECH_PREVIEW")
        HttpEntity body = new StringEntity(json.toString(), ContentType.APPLICATION_JSON)
        method.entity = body
        return execMethod(method)
    }

    private HttpResponse execPut(def url, def json) {
        HttpPut method = new HttpPut(getUriBuilder(url.toString()).build())
        HttpEntity body = new StringEntity(json.toString(), ContentType.APPLICATION_JSON)
        method.entity = body
        return execMethod(method)
    }

    //
    //  NOT CURRENTLY USED
    //

    def registerCiServer(String ciServerName, String ciServerURL) {

        JsonBuilder ciJson = new JsonBuilder()
        ciJson {
            name ciServerName
            url ciServerURL
            server_type "Deployment Automation"

        }

        JsonBuilder body = new JsonBuilder(data: [ciJson.getContent()])
        def ciServersUrl = getBaseUrl() + "/ci_servers"
        HttpResponse response = execPost(ciServersUrl, body)
        checkStatusCode(response.code)

        if (response.code != 201) {
            if (response.code == 409) {
                def json = new JsonSlurper().parseText(response.body)
                def jsonErrors = json?.errors
                def errString = ""
                for (def jsonErr: jsonErrors) {
                    errString += jsonErr.description_translated
                }
                if (jsonErrors) {
                    throw new StepFailedException(errString)
                }
            }

            throw new StepFailedException('Ci Server has not been created.')
        } else {
            def json = new JsonSlurper().parseText(response.body)
            return json?.data[0].id
        }

    }

    private def getCiServer(def ciServerId) {
        HttpResponse response = execGet(getBaseUrl() + "/ci_servers/" + ciServerId)

        if (response.code == 404) {
            return null
        }

        checkStatusCode(response.code)

        return new JsonSlurper().parseText(response.body)
    }

    def registerCiPipeline(String pipelineName, String ciServerId, String rootJobId,
                           String ciJobIds, String ciJobNames) {

        List<JsonBuilder> jobsJson = new ArrayList<>()
        List<String> jobIds = csvToList(ciJobIds)
        List<String> jobNames = csvToList(ciJobNames)
        jobIds.eachWithIndex { def jobId, index ->
            JsonBuilder tmpJson = new JsonBuilder()
            tmpJson {
                jobCiId jobId
                name jobNames.get(index)
            }
            jobsJson.add(tmpJson.getContent())
        }

        JsonBuilder pJson = new JsonBuilder()
        pJson {
            name pipelineName
            server_ci_id ciServerId
            root_job_ci_id rootJobId
            jobs jobsJson

        }

        JsonBuilder body = new JsonBuilder(data: [pJson.getContent()])
        def pipelineUrl = getBaseUrl() + "/pipelines"
        HttpResponse response = execPost(pipelineUrl, body)
        checkStatusCode(response.code)

        if (response.code != 201) {
            if (response.code == 409) {
                def json = new JsonSlurper().parseText(response.body)
                def jsonErrors = json?.errors
                def errString = ""
                for (def jsonErr: jsonErrors) {
                    errString += jsonErr.description_translated
                }
                if (jsonErrors) {
                    throw new StepFailedException(errString)
                }
            }

            throw new StepFailedException('Ci Server has not been created.')
        } else {
            def json = new JsonSlurper().parseText(response.body)
            return json?.data[0].id
        }

    }

    def reportBuildResults(String ciServerId, String jobId, String buildId,
                           String name, String buildStatus, String buildResult,
                           long buildStartTime, long buildDuration) {

        JsonBuilder bJson = new JsonBuilder()
        bJson {
            serverCiId ciServerId
            jobCiId jobId
            buildCiId buildId
            buildName name
            startTime buildStartTime
            duration buildDuration
            status buildStatus
            result buildResult
        }

        println bJson.toPrettyString()

        JsonBuilder body = new JsonBuilder(data: [bJson.getContent()])
        def buildUrl = getBaseUrl() + "/analytics/ci/builds"
        HttpResponse response = execPut(buildUrl, body)
        checkStatusCode(response.code)

        if (response.code != 200) {
            throw new StepFailedException('Ci Build has not been created.')
        }
    }
    

}