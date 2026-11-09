// Replace your existing ParentDashboardScreen fun body start with this:
@Composable
fun ParentDashboardScreen(
    // ... (keep existing parameters)
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedLogForDetail by remember { mutableStateOf<ActivityLogEntity?>(null) }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var logFilter by remember { mutableStateOf("all") } 

    val childName = selectedChild?.name ?: "Child"

    // Use a container with extra padding and rounded corners for a modern feel
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp) // Added side breathing room
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Header: More prominent
            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            // Main Content area with improved spacing
            // ... (rest of your existing logic for tabs)
        }
    }
}
