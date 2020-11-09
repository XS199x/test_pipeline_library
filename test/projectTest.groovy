import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

class ProjectTest extends  BasePipelineTest {

    def project
    def server

    @Before
    void setUp() {
        super.setUp()
        project = loadScript'vars/project.groovy'
        server = loadScript 'vars/server.groovy'
    }

    @Test
    void testGetProjectInfo() {
        project.init("ngx")
//        println project.getCodes()
        println project.getProjectMap('12072_ykb_admin_vue')
//        server.init("dev")
    }

    @Test
    void testGetServers(){
        server.init()
        println server.getDockerBuildServer("dev")
        println server.getDockerRegister("utest")
        println server.getK8s("release")
        println server.getArtifactory()
    }

}