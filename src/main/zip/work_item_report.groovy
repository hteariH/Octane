import com.serena.air.StepFailedException
import com.serena.air.StepPropertiesHelper
import com.serena.air.octane.AuthMethod
import com.serena.air.octane.FailMode
import com.serena.air.octane.OctaneHelper
import com.urbancode.air.AirPluginTool

def apTool = new AirPluginTool(args[0], args[1])
def props = new StepPropertiesHelper(apTool.stepProperties, true)

try {
    String oServerUrl = props.notNull('serverUrl')
    String oClientId = props.notNull('clientId')
    String oClientSecret = props.notNull('password')
    long oSharedSpaceId = props.notNullInt('sharedSpaceId')
    long oWorkspaceId = props.notNullInt('workSpaceId')

    List<String> wiIds = OctaneHelper.csvToList(props.notNull('wiIds'))
    String outputFile = props.notNull('outputFile')

    boolean debugMode = props.optionalBoolean("debugMode", false)

    OctaneHelper oClient = new OctaneHelper(oServerUrl, oClientId, oClientSecret)
    oClient.setAuthMethod(AuthMethod.API_KEY)
    oClient.setSharedSpaceId(oSharedSpaceId)
    oClient.setWorkspaceId(oWorkspaceId)
    oClient.setPreemptiveAuth()
    oClient.setSSL()
    oClient.login()
    oClient.setDebug(debugMode)

    try {
        new File(outputFile).text = oClient.buildWorkItemReport(wiIds)
    } catch (FileNotFoundException ignore) {
        throw new StepFailedException('Check file path!')
    }
    println "Successfully created report in file ${outputFile}"
} catch (StepFailedException e) {
    println "ERROR: ${e.message}"
    System.exit 1
}