"""Assemble docs/*.md into report.md, then report.html (pandoc). PDF: print report.html (see README)."""
import re, subprocess, pathlib
D = pathlib.Path(__file__).parent
SECTIONS = [("Problem Analysis", "PROBLEM_ANALYSIS.md"), ("Requirements Specification", "REQUIREMENTS.md"),
            ("System Architecture", "ARCHITECTURE.md"), ("UML Diagrams", None), ("Technology Stack", "TECH_STACK.md"),
            ("Database Design", "DATABASE.md"), ("Design Patterns and OOP Concepts", "DESIGN_PATTERNS.md"),
            ("Priority Algorithm", "PRIORITY_ALGORITHM.md"), ("Security", "SECURITY.md"), ("Testing", "TEST_CASES.md"),
            ("Design Decisions", "DECISIONS.md"), ("Team", "TEAM.md"), ("Project Demonstration", "DEMO_SCRIPT.md"),
            ("Appendix A: Requirement Traceability", "PLAN.md")]
UML = """The PlantUML sources are in `docs/uml/*.puml`.

### Use case diagram
![Use case diagram](uml/use-case.png)

<div class="pb"></div>

### Class diagram: domain model
Shows classes, attributes, methods, inheritance (User hierarchy, AssignmentRequest), association (reporter, technician), composition (notes, history, feedback, collaborators) and aggregation (audit entries).

![Domain class diagram](uml/class-domain.png)

<div class="pb"></div>

### Class diagram: design patterns (1/2): State, Chain of Responsibility, exceptions
![Patterns 1](uml/class-patterns-1.png)

<div class="pb"></div>

### Class diagram: design patterns (2/2): Strategy, Observer, Factory, Adapter
![Patterns 2](uml/class-patterns-2.png)

<div class="pb"></div>

### State diagram: incident lifecycle
![State diagram](uml/state.png)

<div class="pb"></div>

### Sequence diagrams: the three demo workflows
![W1](uml/seq-w1.png)

<div class="pb"></div>

![W2](uml/seq-w2.png)

<div class="pb"></div>

![W3](uml/seq-w3.png)
"""
out = ['---\ntitle: "BITSolve: Intelligent IT Service Management & Incident Resolution System"\n'
       'subtitle: "CS F213 / MAC F212 Object-Oriented Programming · BITS Pilani Hyderabad · Semester I 2026-27"\n'
       'author: "Group [GROUP_NO] · [NAME1] · [NAME2] · [NAME3] · [NAME4]"\n'
       'date: "Source code: [repository link] · Demo video: [GROUP_NO]_OOP_S1_2025-2026_Project"\n---\n']
for i, (title, f) in enumerate(SECTIONS, 1):
    out.append(f"\n# {i}. {title}\n\n" if not title.startswith("Appendix") else f"\n# {title}\n\n")
    if f is None:
        out.append(UML); continue
    text = (D / f).read_text()
    text = re.sub(r"```mermaid.*?```\n?", "", text, flags=re.S)        # rendered PNG is already embedded
    text = re.sub(r"\A# .*\n", "", text)                              # drop the file's own title
    text = re.sub(r"(?m)^(#{2,5}) ", lambda m: "#" + m.group(1)[1:] + "# ", text)  # keep depth under section
    if f == "ARCHITECTURE.md":
        text += ("\n### Running application (screenshots rendered with sample data)\n\n"
                 "Each role has its own colour, badge, menu and home page (brief: \"difference in GUI for different users\").\n\n"
                 "![Student portal (teal)](images/portal-student.png)\n\n![Technician portal (indigo)](images/portal-technician.png)\n\n"
                 "![Administrator portal (navy/amber): live overview](images/portal-admin.png)\n\n![Student self-registration](images/portal-register.png)\n\n"
                 "![Insights & Analytics page](images/insights-mock.png)\n")
    out.append(text)
(D / "report.md").write_text("".join(out))
subprocess.run(["pandoc", "report.md", "-s", "--toc", "--toc-depth=2", "--metadata", "lang=en", "-c", "report.css",
                "--embed-resources", "-o", "report.html"], cwd=D, check=True)
print("report.md + report.html written")
