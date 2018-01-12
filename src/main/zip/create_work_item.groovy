import com.serena.air.StepFailedException
import com.serena.air.StepPropertiesHelper
import com.serena.air.octane.AuthMethod
import com.serena.air.octane.OctaneHelper
import com.serena.air.octane.EntityType
import com.urbancode.air.AirPluginTool

def apTool = new AirPluginTool(args[0], args[1])
def props = new StepPropertiesHelper(apTool.stepProperties, true)

try {
    String oServerUrl = props.notNull('serverUrl')
    String oClientId = props.notNull('clientId')
    String oClientSecret = props.notNull('password')
    long oSharedSpaceId = props.notNullInt('sharedSpaceId')
    long oWorkspaceId = props.notNullInt('workSpaceId')

    EntityType wiType = EntityType.getType(props.notNull('type'))
    String wiName = props.notNull('name')
    String wiDescription = props.notNull('description')
    String wiParentType = props.optional('parentType')
    String wiParentId = props.optional('parentId')
    long parentId = 0
    if (OctaneHelper.isNotEmpty(wiParentId)) {
        parentId = Long.parseLong(wiParentId)
    }
    String wiProductAreaIds = props.optional('productAreas')
    String wiReleaseId = props.optional('releaseId')
    long releaseId = 0
    if (OctaneHelper.isNotEmpty(wiReleaseId)) {
        releaseId = Long.parseLong(wiReleaseId)
    }
    String wiSeverityId = props.optional('severityId')

    boolean debugMode = props.optionalBoolean("debugMode", false)

    OctaneHelper oClient = new OctaneHelper(oServerUrl, oClientId, oClientSecret)
    oClient.setAuthMethod(AuthMethod.API_KEY)
    oClient.setSharedSpaceId(oSharedSpaceId)
    oClient.setWorkspaceId(oWorkspaceId)
    oClient.setPreemptiveAuth()
    oClient.setSSL()
    oClient.login()
    oClient.setDebug(debugMode)

    def newWi = oClient.createWorkItem(wiType, wiName, wiDescription, wiParentType, parentId,
        wiProductAreaIds, releaseId, wiSeverityId)
    println "Successfully created ${newWi.type} with id: ${newWi.id}"

    apTool.setOutputProperty("wiId", newWi.id)
    apTool.setOutputProperties()

} catch (StepFailedException e) {
    println "ERROR: ${e.message}"
    System.exit 1
}