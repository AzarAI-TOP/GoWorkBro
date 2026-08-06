package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"time"

	"github.com/rs/cors"
)

// ============ Config ============

const (
	defaultPort        = "8765"
	defaultVaultPath   = `C:\Users\ASUS\Documents\Notes`
	ustcNewsFolder     = "USTC 每日要闻"
)

// ============ Models ============

type Todo struct {
	ID                   string `json:"id"`
	Title                string `json:"title"`
	TimingType           string `json:"timing_type"`
	DurationMinutes      int    `json:"duration_minutes"`
	IsCompleted          int    `json:"is_completed"`
	SortOrder            int    `json:"sort_order"`
	KeepTomorrow         int    `json:"keep_tomorrow"`
	CreatedDate          string `json:"created_date"`
	CompletedDate        string `json:"completed_date"`
	ActualDurationSeconds int   `json:"actual_duration_seconds"`
}

type Habit struct {
	ID            string `json:"id"`
	Title         string `json:"title"`
	TargetCount   int    `json:"target_count"`
	Unit          string `json:"unit"`
	SortOrder     int    `json:"sort_order"`
	CreatedDate   string `json:"created_date"`
	CurrentCount  int    `json:"current_count"`
	LastResetDate string `json:"last_reset_date"`
}

type FocusSession struct {
	ID             string `json:"id"`
	TodoID         string `json:"todo_id"`
	SourceType     string `json:"source_type"`
	SourceTitle    string `json:"source_title"`
	StartTime      string `json:"start_time"`
	EndTime        string `json:"end_time"`
	DurationSeconds int   `json:"duration_seconds"`
	SessionDate    string `json:"session_date"`
}

type SyncRequest struct {
	Todos  []Todo  `json:"todos"`
	Habits []Habit `json:"habits"`
}

type UstcNewsResponse struct {
	Date     string `json:"date"`
	Title    string `json:"title"`
	Markdown string `json:"markdown"`
}

// ============ Storage (in-memory, simple sync cache) ============

var (
	syncedTodos  = make(map[string]Todo)
	syncedHabits = make(map[string]Habit)
	vaultPath    = defaultVaultPath
)

// ============ Main ============

func main() {
	port := os.Getenv("GOWORKBRO_PORT")
	if port == "" {
		port = defaultPort
	}
	if v := os.Getenv("OBSIDIAN_VAULT_PATH"); v != "" {
		vaultPath = v
	}

	mux := http.NewServeMux()

	// Health check
	mux.HandleFunc("/health", healthHandler)

	// API endpoints
	mux.HandleFunc("/api/todos/sync", syncTodosHandler)
	mux.HandleFunc("/api/todos", todosHandler)
	mux.HandleFunc("/api/habits/sync", syncHabitsHandler)
	mux.HandleFunc("/api/habits", habitsHandler)
	mux.HandleFunc("/api/focus-sessions", focusSessionsHandler)
	mux.HandleFunc("/api/ustc-news/today", ustcNewsTodayHandler)
	mux.HandleFunc("/api/ustc-news", ustcNewsByDateHandler)
	mux.HandleFunc("/api/ustc-news/dates", ustcNewsDatesHandler)

	// CORS
	handler := cors.AllowAll().Handler(mux)

	addr := fmt.Sprintf(":%s", port)
	log.Printf("🚀 GoWorkBro backend server starting on http://localhost:%s", port)
	log.Printf("📁 Obsidian vault: %s", vaultPath)
	log.Fatal(http.ListenAndServe(addr, handler))
}

// ============ Handlers ============

func healthHandler(w http.ResponseWriter, r *http.Request) {
	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"status":  "ok",
		"service": "GoWorkBro API",
		"version": "1.0.0",
		"time":    time.Now().Format(time.RFC3339),
	})
}

func syncTodosHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		jsonError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var req SyncRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		jsonError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	for _, todo := range req.Todos {
		syncedTodos[todo.ID] = todo
	}

	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"status":  "synced",
		"count":   len(req.Todos),
		"message": "todos synced successfully",
	})
}

func todosHandler(w http.ResponseWriter, r *http.Request) {
	switch r.Method {
	case http.MethodGet:
		todos := make([]Todo, 0, len(syncedTodos))
		for _, t := range syncedTodos {
			todos = append(todos, t)
		}
		jsonResponse(w, http.StatusOK, map[string]interface{}{
			"todos": todos,
		})
	case http.MethodDelete:
		id := r.URL.Query().Get("id")
		if id != "" {
			delete(syncedTodos, id)
			jsonResponse(w, http.StatusOK, map[string]string{"status": "deleted"})
			return
		}
		// Clear all
		syncedTodos = make(map[string]Todo)
		jsonResponse(w, http.StatusOK, map[string]string{"status": "cleared"})
	default:
		jsonError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

func syncHabitsHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		jsonError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var req SyncRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		jsonError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	for _, habit := range req.Habits {
		syncedHabits[habit.ID] = habit
	}

	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"status":  "synced",
		"count":   len(req.Habits),
		"message": "habits synced successfully",
	})
}

func habitsHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		jsonError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	habits := make([]Habit, 0, len(syncedHabits))
	for _, h := range syncedHabits {
		habits = append(habits, h)
	}
	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"habits": habits,
	})
}

func focusSessionsHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		jsonError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var session FocusSession
	if err := json.NewDecoder(r.Body).Decode(&session); err != nil {
		jsonError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	// Log the focus session (in a real app, store in DB)
	log.Printf("📝 Focus session: %s - %d seconds", session.SourceTitle, session.DurationSeconds)

	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"status":  "recorded",
		"session": session,
	})
}

// ============ USTC News ============

func ustcNewsTodayHandler(w http.ResponseWriter, r *http.Request) {
	now := time.Now()
	dateStr := now.Format("2006-01-02")
	serveUstcNews(w, dateStr)
}

func ustcNewsByDateHandler(w http.ResponseWriter, r *http.Request) {
	date := r.URL.Query().Get("date")
	if date == "" {
		ustcNewsTodayHandler(w, r)
		return
	}
	serveUstcNews(w, date)
}

func serveUstcNews(w http.ResponseWriter, dateStr string) {
	newsDir := filepath.Join(vaultPath, ustcNewsFolder)
	filePath := filepath.Join(newsDir, dateStr+".md")

	content, err := os.ReadFile(filePath)
	if err != nil {
		jsonError(w, http.StatusNotFound, fmt.Sprintf("news not found for date %s", dateStr))
		return
	}

	// Parse markdown
	md := string(content)

	// Strip frontmatter
	if strings.HasPrefix(md, "---") {
		endIdx := strings.Index(md[3:], "---")
		if endIdx > 0 {
			md = strings.TrimSpace(md[endIdx+6:])
		}
	}

	// Extract title from first H1
	title := fmt.Sprintf("USTC 每日要闻 — %s", dateStr)
	titleRe := regexp.MustCompile(`(?m)^#\s+(.+)$`)
	if match := titleRe.FindStringSubmatch(md); len(match) > 1 {
		title = strings.TrimSpace(match[1])
	}

	jsonResponse(w, http.StatusOK, UstcNewsResponse{
		Date:     dateStr,
		Title:    title,
		Markdown: md,
	})
}

func ustcNewsDatesHandler(w http.ResponseWriter, r *http.Request) {
	newsDir := filepath.Join(vaultPath, ustcNewsFolder)
	entries, err := os.ReadDir(newsDir)
	if err != nil {
		jsonError(w, http.StatusNotFound, "ustc news directory not found")
		return
	}

	dateRe := regexp.MustCompile(`^(\d{4}-\d{2}-\d{2})\.md$`)
	var dates []string
	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		if match := dateRe.FindStringSubmatch(entry.Name()); len(match) > 1 {
			dates = append(dates, match[1])
		}
	}

	// Sort descending (newest first)
	sort.Sort(sort.Reverse(sort.StringSlice(dates)))

	jsonResponse(w, http.StatusOK, map[string]interface{}{
		"dates": dates,
		"count": len(dates),
	})
}

// ============ Helpers ============

func jsonResponse(w http.ResponseWriter, code int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(data)
}

func jsonError(w http.ResponseWriter, code int, message string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(map[string]string{"error": message})
}
