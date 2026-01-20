package com.qiyi.tools;


import com.qiyi.tools.context.ConsoleToolContext;
import com.qiyi.tools.example.HelloWorldTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ToolManagerTest {

    @BeforeEach
    public void setUp() {
        // Register HelloWorldTool if not present (ToolManager.init() does this, but we want to be sure)
        ToolRegistry.register(new HelloWorldTool());
    }

    @Test
    public void testDirectCommandExecution() {
        // Mock context using ConsoleToolContext which prints to stdout
        // We can't easily capture stdout here without more complex setup, 
        // but we can ensure it doesn't crash and returns normally.
        // To verify it actually ran, we could use a spy tool, but HelloWorldTool is simple.
        
        // This test mainly verifies that the Direct Command path is taken and doesn't crash.
        // If LLM was used (and config missing), it would print errors (but not throw).
        // If Direct Command is used, it should print "Directly executing tool: hello_world"
        
        ToolManager.analyzeAndExecute("hello_world name=\"TestUser\"", new ConsoleToolContext());
    }
    
    @Test
    public void testDirectCommandExecutionWithoutParams() {
        ToolManager.analyzeAndExecute("hello_world", new ConsoleToolContext());
    }

    @Test
    public void testDirectCommandFallback() {
        ConsoleToolContext context = new ConsoleToolContext();

        // 1. Direct Command: No args -> True
        assertTrue(ToolManager.tryExecuteDirectCommand("hello_world", context), "Should execute direct command for 'hello_world'");

        // 2. Direct Command: With key=value -> True
        assertTrue(ToolManager.tryExecuteDirectCommand("hello_world name=Test", context), "Should execute direct command for 'hello_world name=Test'");

        // 3. Fallback: Args present but no key=value -> False
        assertFalse(ToolManager.tryExecuteDirectCommand("hello_world some random text", context), "Should fallback to LLM for 'hello_world some random text'");

        // 4. Fallback: Non-existent tool -> False
        assertFalse(ToolManager.tryExecuteDirectCommand("non_existent_tool", context), "Should fallback to LLM for non-existent tool");
        
        // 5. Direct Command: Mixed args (contains key=value) -> True
        assertTrue(ToolManager.tryExecuteDirectCommand("hello_world name=Test extra", context), "Should execute direct command if at least one key=value is found");
    }
}
