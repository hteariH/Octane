package test.com.serena.air.octane

import com.serena.air.octane.AuthMethod
import com.serena.air.octane.EntityType
import com.serena.air.octane.FailMode
import com.serena.air.octane.OctaneHelper

def oServerUrl = "http://localhost:6060"
def oClientId = "da-api-user_7d89ro1vqo3lofje8zn40y2gz"
def oClientSecret = "%f6e4c2715531be54H"
def oSharedSpaceId = 1001
def oWorkspaceId = 1002

OctaneHelper oClient = new OctaneHelper(oServerUrl, oClientId, oClientSecret)
oClient.setAuthMethod(AuthMethod.API_KEY)
oClient.setSharedSpaceId(oSharedSpaceId)
oClient.setWorkspaceId(oWorkspaceId)
oClient.setPreemptiveAuth()
oClient.setSSL()
oClient.login()
oClient.setDebug(true)

List<String> eIds = new ArrayList<>()

def newDefect =  oClient.createWorkItem(EntityType.DEFECT,
        "example defect", "its description",
        "work_item_root", 1001,
        "1001,1002", 1001,
        "list_node.severity.high")
println "Successfully created defect ${newDefect.id}"
def newStory =  oClient.createWorkItem(EntityType.STORY,
        "example story", "its description",
        "work_item_root", 1001,
        "1001,1002", 1001, "")
println "Successfully created story ${newStory.id}"

eIds.add("${newDefect.id}")
eIds.add("${newStory.id}")

println "${newDefect.id} is a work item of type: " + oClient.getWorkItemType(Long.parseLong(newDefect.id))
println "${newStory.id} is a work item of type: " + oClient.getWorkItemType(Long.parseLong(newStory.id))

oClient.updateWorkItems(eIds, "phase.defect.opened",
        "updated by DA", 1001, FailMode.WARN_ONLY)

oClient.commentWorkItems(eIds, "another comment", 1001, FailMode.WARN_ONLY)

oClient.checkWorkItemStatus(eIds, "phase.defect.closed", FailMode.WARN_ONLY)

println oClient.buildWorkItemReport(eIds)

