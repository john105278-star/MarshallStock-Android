from pathlib import Path

p = Path('windows/marshall_stock_v30.py')
s = p.read_text(encoding='utf-8')

s = s.replace('APP_NAME = "Marshall Stock PRO v3.0"', 'APP_NAME = "Marshall Stock PRO v3.2 VISION"')

# Slightly larger base fonts where the original source uses compact sizes.
s = s.replace('(\"Malgun Gothic\",10)', '(\"Malgun Gothic\",11)')
s = s.replace('(\"Malgun Gothic\",10,\"bold\")', '(\"Malgun Gothic\",11,\"bold\")')
s = s.replace('(\"Malgun Gothic\",11)', '(\"Malgun Gothic\",12)')
s = s.replace('(\"Malgun Gothic\",11,\"bold\")', '(\"Malgun Gothic\",12,\"bold\")')

vision = r'''
# ===== Marshall Stock PRO v3.2 VISION readability layer =====
_MARSHALL_TREE_INSERT = ttk.Treeview.insert
_MARSHALL_MAINLOOP = tk.Tk.mainloop
_MARSHALL_VISION_READY = False


def _marshall_font_tuple(widget, default_size=11, bold=False):
    try:
        raw = widget.cget("font")
        if isinstance(raw, tuple) and len(raw) >= 2:
            size = int(raw[1]) if str(raw[1]).lstrip('-').isdigit() else default_size
            weight = "bold" if (len(raw) >= 3 and "bold" in str(raw[2]).lower()) or bold else "normal"
            return ("Malgun Gothic", max(default_size, abs(size)), weight)
    except Exception:
        pass
    return ("Malgun Gothic", default_size, "bold" if bold else "normal")


def _marshall_style_tree(tree):
    try:
        tree.tag_configure("vision_even", background="#0f1a2d", foreground="#f8fafc")
        tree.tag_configure("vision_odd", background="#121f34", foreground="#f8fafc")
        tree.tag_configure("vision_strong", background="#123326", foreground="#bbf7d0")
        tree.tag_configure("vision_risk", background="#351a22", foreground="#fecaca")
        for i, iid in enumerate(tree.get_children("")):
            vals = [str(v).strip() for v in tree.item(iid, "values")]
            if any(v in ("A+", "A", "A-") for v in vals):
                tree.item(iid, tags=("vision_strong",))
            elif any(v in ("위험", "약세", "급락", "순매도") for v in vals):
                tree.item(iid, tags=("vision_risk",))
            else:
                tree.item(iid, tags=(("vision_even" if i % 2 == 0 else "vision_odd"),))
    except Exception:
        pass


def _marshall_vision_insert(self, parent, index, iid=None, **kw):
    try:
        vals = [str(v).strip() for v in kw.get("values", ())]
        if "tags" not in kw or not kw.get("tags"):
            if any(v in ("A+", "A", "A-") for v in vals):
                kw["tags"] = ("vision_strong",)
            elif any(v in ("위험", "약세", "급락", "순매도") for v in vals):
                kw["tags"] = ("vision_risk",)
            else:
                try:
                    n = len(self.get_children(parent))
                except Exception:
                    n = 0
                kw["tags"] = (("vision_even" if n % 2 == 0 else "vision_odd"),)
    except Exception:
        pass
    if iid is None:
        return _MARSHALL_TREE_INSERT(self, parent, index, **kw)
    return _MARSHALL_TREE_INSERT(self, parent, index, iid=iid, **kw)


def _marshall_apply_widget(widget):
    """Apply a higher-contrast, roomier dark UI without changing business logic."""
    try:
        cls = widget.winfo_class()
    except Exception:
        cls = ""

    # Map old dark colors into a clearer two-level panel system.
    try:
        bg = str(widget.cget("bg")).lower()
        bg_map = {
            "#0f172a": "#0b1220",
            "#111827": "#111c2f",
            "#0b1220": "#08111f",
            "#1f2937": "#172033",
        }
        if bg in bg_map:
            widget.configure(bg=bg_map[bg])
    except Exception:
        pass

    try:
        if isinstance(widget, tk.Label):
            current = str(widget.cget("fg")).lower()
            if current in ("white", "#ffffff", "#e5e7eb", "#d1d5db", "#cbd5e1"):
                widget.configure(fg="#f8fafc")
            elif current in ("#60a5fa", "#38bdf8"):
                widget.configure(fg="#7dd3fc")
            elif current in ("#fbbf24", "#f59e0b"):
                widget.configure(fg="#fde047")
            f = _marshall_font_tuple(widget, 11)
            widget.configure(font=f)

        elif isinstance(widget, tk.Button):
            widget.configure(
                font=("Malgun Gothic", 11, "bold"), relief="flat", bd=0,
                padx=15, pady=8, cursor="hand2", takefocus=True
            )
            try:
                b = str(widget.cget("bg")).lower()
                if b in ("#0f172a", "#111827", "#111c2f", "systembuttonface"):
                    widget.configure(bg="#1d4ed8", fg="white", activebackground="#2563eb", activeforeground="white")
            except Exception:
                pass

        elif isinstance(widget, tk.Entry):
            widget.configure(
                bg="#0b1425", fg="#f8fafc", insertbackground="#f8fafc",
                font=("Malgun Gothic", 12), relief="flat", bd=0,
                highlightthickness=1, highlightbackground="#334155", highlightcolor="#38bdf8"
            )

        elif isinstance(widget, tk.Text):
            widget.configure(
                bg="#0b1425", fg="#e2e8f0", insertbackground="#f8fafc",
                font=("Malgun Gothic", 11), relief="flat", bd=0,
                padx=14, pady=12, spacing1=2, spacing3=4,
                highlightthickness=1, highlightbackground="#263449", highlightcolor="#38bdf8"
            )

        elif isinstance(widget, tk.Listbox):
            widget.configure(
                bg="#0b1425", fg="#e2e8f0", selectbackground="#2563eb",
                selectforeground="white", font=("Malgun Gothic", 11),
                relief="flat", bd=0, highlightthickness=1, highlightbackground="#263449"
            )

        elif isinstance(widget, tk.PanedWindow):
            widget.configure(bg="#0b1220", sashwidth=7, bd=0, relief="flat")

        elif isinstance(widget, ttk.Treeview):
            _marshall_style_tree(widget)
    except Exception:
        pass

    try:
        for child in widget.winfo_children():
            _marshall_apply_widget(child)
    except Exception:
        pass


def _marshall_apply_vision(root):
    global _MARSHALL_VISION_READY
    if _MARSHALL_VISION_READY:
        return
    _MARSHALL_VISION_READY = True

    try:
        root.configure(bg="#0b1220")
        root.minsize(1280, 760)
        try:
            root.state("zoomed")
        except Exception:
            root.geometry("1550x900")
        root.option_add("*Font", "{Malgun Gothic} 11")
    except Exception:
        pass

    style = ttk.Style(root)
    try:
        style.theme_use("clam")
    except Exception:
        pass

    style.configure("TNotebook", background="#0b1220", borderwidth=0, tabmargins=(8, 8, 8, 0))
    style.configure("TNotebook.Tab", background="#172033", foreground="#cbd5e1", font=("Malgun Gothic", 11, "bold"), padding=(18, 11), borderwidth=0)
    style.map("TNotebook.Tab",
              background=[("selected", "#2563eb"), ("active", "#1e3a5f")],
              foreground=[("selected", "#ffffff"), ("active", "#ffffff")])

    style.configure("Treeview", background="#0f1a2d", fieldbackground="#0f1a2d", foreground="#f8fafc", rowheight=34, font=("Malgun Gothic", 11), borderwidth=0, relief="flat")
    style.configure("Treeview.Heading", background="#1e293b", foreground="#f8fafc", font=("Malgun Gothic", 11, "bold"), padding=(8, 9), borderwidth=0, relief="flat")
    style.map("Treeview", background=[("selected", "#2563eb")], foreground=[("selected", "#ffffff")])
    style.map("Treeview.Heading", background=[("active", "#334155")])

    style.configure("TCombobox", font=("Malgun Gothic", 11), padding=(8, 7), foreground="#0f172a")
    style.configure("TProgressbar", troughcolor="#172033", background="#38bdf8", borderwidth=0, thickness=13)
    style.configure("Vertical.TScrollbar", background="#334155", troughcolor="#0b1220", arrowcolor="#e2e8f0", borderwidth=0)
    style.configure("Horizontal.TScrollbar", background="#334155", troughcolor="#0b1220", arrowcolor="#e2e8f0", borderwidth=0)

    # Future rows receive zebra/strength tags as they are inserted.
    ttk.Treeview.insert = _marshall_vision_insert
    _marshall_apply_widget(root)

    # Re-apply after initial async layout/data population.
    try:
        root.after(700, lambda: _marshall_apply_widget(root))
        root.after(1800, lambda: _marshall_apply_widget(root))
    except Exception:
        pass


def _marshall_vision_mainloop(self, *args, **kwargs):
    _marshall_apply_vision(self)
    return _MARSHALL_MAINLOOP(self, *args, **kwargs)


tk.Tk.mainloop = _marshall_vision_mainloop
# ===== end VISION layer =====
'''

marker = 'if __name__'
pos = s.rfind(marker)
if pos < 0:
    raise SystemExit('main guard not found for v3.2 UI patch')
s = s[:pos] + vision + '\n\n' + s[pos:]

p.write_text(s, encoding='utf-8')
print('v3.2 VISION readability patch applied')
