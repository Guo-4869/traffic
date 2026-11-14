import com.traffic.core.*;
import com.traffic.core.SessionEngine.*;
import java.util.*;
import java.time.*;

public final class CoreCheck {
    static int checks;
    static final class Fixture {
        final State state = new State();
        final List<Session> sessions = new ArrayList<>();
        final List<Transition> transitions = new ArrayList<>();
        final SessionEngine engine = new SessionEngine(state,sessions,transitions);
        Fixture() { state.interactive = true; state.unlocked = true; }
        void e(long t, Kind k, String pkg, int id) { engine.accept(new Event(t,k,pkg,id)); }
        void e(long t, Kind k) { e(t,k,null,0); }
    }
    static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Fixture f = new Fixture();
        f.e(1000,Kind.RESUME,"A",1); f.e(2000,Kind.PAUSE,"A",1);
        f.e(7000,Kind.RESUME,"A",2); f.e(9000,Kind.PAUSE,"A",2);
        check(f.sessions.size()==1,"exactly 5s merge across batches");
        check(f.sessions.get(0).start==1000 && f.sessions.get(0).end==9000,"merge endpoints");
        f.e(14001,Kind.RESUME,"A",3);
        check(f.sessions.size()==2,"more than 5s remains distinct");

        f = new Fixture();
        f.e(1000,Kind.RESUME,"A",1); f.e(2000,Kind.RESUME,"A",2);
        f.e(2500,Kind.PAUSE,"A",1);
        check(f.sessions.get(0).end==null,"late old activity pause must not close new activity");
        f.e(3000,Kind.PAUSE,"A",2);
        check(f.sessions.get(0).end==3000,"last activity closes package");
        f.e(3000,Kind.PAUSE,"A",2);
        check(f.sessions.size()==1,"duplicate pause idempotent");

        f = new Fixture();
        f.e(1000,Kind.RESUME,"A",1); f.e(2000,Kind.PAUSE,"A",1);
        f.e(2500,Kind.RESUME,"B",2); f.e(3000,Kind.PAUSE,"B",2);
        f.e(4000,Kind.RESUME,"A",3);
        check(f.sessions.size()==2,"short A return merged into same A session");
        check(f.transitions.size()==2,"A B A flow must survive session merge");
        var summary = Reports.summarize(f.sessions,f.transitions,0,10000,5000);
        check(summary.flows().size()==2,"directed edges kept separate");
        check(summary.apps().stream().filter(a->a.pkg().equals("A")).findFirst().get().duration()==2000,"merged session must exclude time spent in B");

        f = new Fixture();
        f.e(1000,Kind.RESUME,"A",1); f.e(2000,Kind.SCREEN_OFF);
        f.e(2100,Kind.RESUME,"B",2); f.e(2200,Kind.PAUSE,"B",2);
        f.e(2300,Kind.LOCK); f.e(2500,Kind.SCREEN_ON);
        check(f.sessions.size()==1,"no collection during screen-off or lock");
        f.e(3000,Kind.UNLOCK);
        check(f.sessions.size()==2 && f.sessions.get(1).start==3000,"unlock resumes eligible intervals");
        check(f.transitions.isEmpty(),"no flows across lock boundary");
        check(f.sessions.get(0).end==2000,"screen boundary clips old session");

        f = new Fixture();
        f.e(1000,Kind.RESUME,"A",1); f.e(2000,Kind.SHUTDOWN);
        check(f.sessions.get(0).end==null && "unknown".equals(f.sessions.get(0).endReason),"shutdown must not invent close timestamp");
        check(f.state.active.isEmpty(),"shutdown clears activity state");
        summary=Reports.summarize(f.sessions,0,10000,10000);
        check(summary.apps().get(0).duration()==0,"unknown endpoint excluded from duration");
        check(summary.apps().get(0).unknownEnds()==1,"unknown endpoints disclosed");

        List<Session> sessions = new ArrayList<>();
        Session a = new Session("A",1000); a.end=10000L; a.endReason="pause"; a.activeSince=null; a.spans.add(new Span(1000,10000)); sessions.add(a);
        summary=Reports.summarize(sessions,3000,7000,12000);
        check(summary.apps().get(0).duration()==4000,"cross-day overlap clipped");
        check(summary.apps().get(0).opens()==0,"carry-over not a new open");
        SessionEngine.prune(sessions,5000);
        check(sessions.get(0).start==5000 && sessions.get(0).clipped,"7-day start timestamp clipped");
        SessionEngine.prune(sessions,10000);
        check(sessions.isEmpty(),"expired rows removed at boundary");

        ZoneId zone=ZoneId.of("America/New_York");
        long t=Instant.parse("2026-03-08T17:00:00Z").toEpochMilli();
        long next=LocalDate.of(2026,3,9).atStartOfDay(zone).toInstant().toEpochMilli();
        check(next-Reports.dayStart(t,zone)==23*3600000L,"DST day is not always 24h");
        check(Instant.ofEpochMilli(Reports.weekStart(t,zone)).atZone(zone).getDayOfWeek()==DayOfWeek.MONDAY,"calendar week starts Monday");
        System.out.println("CORE CHECKS PASSED: "+checks);
    }
}
