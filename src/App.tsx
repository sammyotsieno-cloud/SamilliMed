import React, { useState, useEffect, useMemo } from 'react';
import {
  Search,
  Plus,
  ArrowLeft,
  ChevronRight,
  MoreVertical,
  Pill,
  Syringe,
  Microscope,
  Stethoscope,
  Bandage,
  ShieldAlert,
  Baby,
  Sparkles,
  Apple,
  Building2,
  Trash2,
  Edit2,
  X,
  Layers,
  ArrowUpRight,
  QrCode,
  DollarSign,
  Package,
  CheckCircle2,
  AlertCircle,
  FileSpreadsheet,
  RotateCcw,
  Sliders,
  User,
  Heart,
  BookOpen,
  Activity,
  AlertTriangle,
  HeartPulse,
  Download,
  Check,
  ShieldCheck,
  Clock,
  Sparkle,
  Info,
  Truck,
  ShoppingCart,
  BarChart3,
  AlertOctagon,
  RefreshCw,
  FileText,
  Settings,
  Play
} from 'lucide-react';

export interface TaxonomyNode {
  id: string;
  parentId: string | null;
  name: string;
  description: string | null;
}

export interface ClinicalProfile {
  atc?: string;
  indications?: string;
  mechanism?: string;
  pharmacokinetics?: string;
  pearls?: string;
  contraindications?: string;
  dosageForms?: string;
  monitoring?: string;
}

export function parseClinicalProfile(desc: string | null): ClinicalProfile {
  if (!desc) return {};
  try {
    const parsed = JSON.parse(desc);
    if (typeof parsed === 'object' && parsed !== null) {
      return parsed;
    }
  } catch {
    // If plain text
    return { indications: desc };
  }
  return { indications: desc };
}

const DEFAULT_ROOTS: TaxonomyNode[] = [
  { id: "1", parentId: null, name: "Medicines & Pharmaceuticals", description: "Comprehensive reference clinical taxonomy of medicinal substances, pharmacological classes, therapeutic indications, and essential drug formulations across 19 clinical medicine domains." },
  { id: "2", parentId: null, name: "Medical Consumables", description: "Single-use disposables, personal protective gear, catheters, and clinical consumables." },
  { id: "3", parentId: null, name: "Diagnostic / Laboratory Supplies", description: "Reagents, rapid test kits, collection tubes, microscopy supplies, and laboratory consumables." },
  { id: "4", parentId: null, name: "Medical Devices & Equipment", description: "Reusable clinical instruments, diagnostic devices, monitoring equipment, and procedural apparatus." },
  { id: "5", parentId: null, name: "Wound Care & Procedure Supplies", description: "Gauze, bandages, sterile dressings, surgical sutures, tapes, and procedural packs." },
  { id: "6", parentId: null, name: "Infection Prevention & Control", description: "Hospital-grade antiseptics, high-level disinfectants, sterilisation monitors, and barrier supplies." },
  { id: "7", parentId: null, name: "Maternal, Newborn & Family Planning", description: "Obstetric delivery kits, contraceptive commodities, neonatal care items, and reproductive health commodities." },
  { id: "8", parentId: null, name: "Personal Care / Hygiene", description: "Patient cleansing items, skin care barriers, adult briefs, and institutional hygiene commodities." },
  { id: "9", parentId: null, name: "Nutrition & Supplements", description: "Therapeutic nutrition, enteral feeds, dietary formulations, oral rehydration salts, and macronutrients." },
  { id: "10", parentId: null, name: "Non-medical / Facility Supplies", description: "Administrative forms, facility stationery, biohazard management bags, and operational utility supplies." }
];

export default function App() {
  const [currentView, setCurrentView] = useState<'dashboard' | 'products'>('dashboard');
  const [showSplash, setShowSplash] = useState(true);
  const [nodes, setNodes] = useState<TaxonomyNode[]>(() => {
    const cached = localStorage.getItem('samillimed_taxonomy_custom_v2');
    if (cached) {
      try {
        const parsed = JSON.parse(cached);
        if (Array.isArray(parsed) && parsed.length > 0) return parsed;
      } catch {
        // fallback
      }
    }
    return DEFAULT_ROOTS;
  });
  const [isLoading, setIsLoading] = useState(true);
  
  // Products Database Navigation Stack
  const [navStack, setNavStack] = useState<TaxonomyNode[]>([]);
  const currentParent = navStack[navStack.length - 1] || null;

  // Search state
  const [searchQuery, setSearchQuery] = useState('');
  const [isSearchOpen, setIsSearchOpen] = useState(false);

  // Active Tab for Segmented Clinical View
  const [activeTab, setActiveTab] = useState<'indications' | 'mechanism' | 'pearls' | 'dosage' | 'all'>('indications');

  // Dialogs
  const [showAddDialog, setShowAddDialog] = useState(false);
  const [editingNode, setEditingNode] = useState<TaxonomyNode | null>(null);
  const [deletingNode, setDeletingNode] = useState<TaxonomyNode | null>(null);
  const [selectedSubstance, setSelectedSubstance] = useState<TaxonomyNode | null>(null);
  const [showSaveToast, setShowSaveToast] = useState(false);

  // Clinical Edit Form states
  const [editTab, setEditTab] = useState<'basic' | 'clinical' | 'safety' | 'delivery'>('basic');
  const [formName, setFormName] = useState('');
  const [formAtc, setFormAtc] = useState('');
  const [formIndications, setFormIndications] = useState('');
  const [formMechanism, setFormMechanism] = useState('');
  const [formPk, setFormPk] = useState('');
  const [formPearls, setFormPearls] = useState('');
  const [formContraindications, setFormContraindications] = useState('');
  const [formDosageForms, setFormDosageForms] = useState('');
  const [formMonitoring, setFormMonitoring] = useState('');

  // Dashboard Facility state (with excitability)
  const [facilityName, setFacilityName] = useState(() => {
    return localStorage.getItem('samillimed_facility_name') || 'SamilliMed Medical Centre';
  });
  const [isEditingFacility, setIsEditingFacility] = useState(false);
  const [facilityDraft, setFacilityDraft] = useState(facilityName);

  // Quick Action Dialogs for Dashboard
  const [activeDashboardAction, setActiveDashboardAction] = useState<string | null>(null);

  // Auto-dismiss launch splash screen after 2.5s
  useEffect(() => {
    if (showSplash) {
      const timer = setTimeout(() => {
        setShowSplash(false);
      }, 2500);
      return () => clearTimeout(timer);
    }
  }, [showSplash]);

  // Fetch full taxonomy JSON on mount if not in localStorage
  useEffect(() => {
    const cached = localStorage.getItem('samillimed_taxonomy_custom_v2');
    if (cached) {
      try {
        const parsed = JSON.parse(cached);
        if (Array.isArray(parsed) && parsed.length > 50) {
          setNodes(parsed);
          setIsLoading(false);
          return;
        }
      } catch {
        // proceed to fetch
      }
    }

    let isMounted = true;
    fetch('/taxonomy_v2.json')
      .then(res => {
        if (!res.ok) throw new Error('Network error');
        return res.json();
      })
      .then((data: TaxonomyNode[]) => {
        if (isMounted && Array.isArray(data) && data.length > 0) {
          setNodes(data);
          setIsLoading(false);
        }
      })
      .catch(() => {
        if (isMounted) {
          setIsLoading(false);
        }
      });
    return () => { isMounted = false; };
  }, []);

  // Save changes to localStorage whenever nodes change
  const persistNodes = (newNodes: TaxonomyNode[]) => {
    setNodes(newNodes);
    try {
      localStorage.setItem('samillimed_taxonomy_custom_v2', JSON.stringify(newNodes));
    } catch {
      // Storage quota exceeded or disabled
    }
  };

  // Reset to default reference database
  const handleResetToDefault = async () => {
    if (window.confirm("Reset all custom modifications to standard WHO/SamilliMed clinical reference dataset?")) {
      setIsLoading(true);
      try {
        const res = await fetch('/taxonomy_v2.json');
        const data = await res.json();
        localStorage.removeItem('samillimed_taxonomy_custom_v2');
        setNodes(data);
        if (selectedSubstance) {
          const fresh = data.find((n: TaxonomyNode) => n.id === selectedSubstance.id);
          setSelectedSubstance(fresh || null);
        }
        setShowSaveToast(true);
        setTimeout(() => setShowSaveToast(false), 3000);
      } catch (err) {
        alert("Failed to reload default dataset.");
      } finally {
        setIsLoading(false);
      }
    }
  };

  // Export database as JSON
  const handleExportDatabase = () => {
    const blob = new Blob([JSON.stringify(nodes, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `SamilliMed_Taxonomy_V2_Clinical_${new Date().toISOString().split('T')[0]}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  // Children of current parent
  const currentChildren = useMemo(() => {
    if (!currentParent) {
      return nodes.filter(n => !n.parentId);
    }
    return nodes.filter(n => n.parentId === currentParent.id);
  }, [nodes, currentParent]);

  // Child count map for fast lookup
  const childCounts = useMemo(() => {
    const counts: Record<string, number> = {};
    for (const node of nodes) {
      if (node.parentId) {
        counts[node.parentId] = (counts[node.parentId] || 0) + 1;
      }
    }
    return counts;
  }, [nodes]);

  // Search filter
  const searchResults = useMemo(() => {
    if (!searchQuery.trim()) return [];
    const q = searchQuery.toLowerCase();
    return nodes
      .filter(n => {
        if (n.name.toLowerCase().includes(q)) return true;
        if (n.id.toLowerCase().includes(q)) return true;
        if (n.description && n.description.toLowerCase().includes(q)) return true;
        return false;
      })
      .slice(0, 40);
  }, [nodes, searchQuery]);

  // Open edit modal for a node
  const handleStartEdit = (node: TaxonomyNode) => {
    setEditingNode(node);
    setFormName(node.name);
    const profile = parseClinicalProfile(node.description);
    setFormAtc(profile.atc || '');
    setFormIndications(profile.indications || '');
    setFormMechanism(profile.mechanism || '');
    setFormPk(profile.pharmacokinetics || '');
    setFormPearls(profile.pearls || '');
    setFormContraindications(profile.contraindications || '');
    setFormDosageForms(profile.dosageForms || '');
    setFormMonitoring(profile.monitoring || '');
    setEditTab('basic');
  };

  // Save edited node
  const handleSaveEdit = () => {
    if (!editingNode || !formName.trim()) return;

    const profile: ClinicalProfile = {
      atc: formAtc.trim() || undefined,
      indications: formIndications.trim() || undefined,
      mechanism: formMechanism.trim() || undefined,
      pharmacokinetics: formPk.trim() || undefined,
      pearls: formPearls.trim() || undefined,
      contraindications: formContraindications.trim() || undefined,
      dosageForms: formDosageForms.trim() || undefined,
      monitoring: formMonitoring.trim() || undefined,
    };

    const hasClinicalFields = Object.values(profile).some(v => v !== undefined);
    const updatedDesc = hasClinicalFields ? JSON.stringify(profile) : null;

    const updated = nodes.map(n => {
      if (n.id === editingNode.id) {
        return {
          ...n,
          name: formName.trim(),
          description: updatedDesc
        };
      }
      return n;
    });

    persistNodes(updated);
    
    // Update active references
    if (selectedSubstance && selectedSubstance.id === editingNode.id) {
      setSelectedSubstance({
        ...editingNode,
        name: formName.trim(),
        description: updatedDesc
      });
    }

    if (currentParent && currentParent.id === editingNode.id) {
      const updatedParent = {
        ...currentParent,
        name: formName.trim(),
        description: updatedDesc
      };
      setNavStack(prev => prev.map(p => p.id === updatedParent.id ? updatedParent : p));
    }

    setEditingNode(null);
    setShowSaveToast(true);
    setTimeout(() => setShowSaveToast(false), 2500);
  };

  // Add new node
  const handleAddNode = () => {
    if (!formName.trim()) return;

    let newId: string;
    if (!currentParent) {
      const rootIds = nodes.filter(n => !n.parentId).map(n => parseInt(n.id)).filter(n => !isNaN(n));
      const maxId = rootIds.length > 0 ? Math.max(...rootIds) : 0;
      newId = `${maxId + 1}`;
    } else {
      const siblingIds = nodes
        .filter(n => n.parentId === currentParent.id)
        .map(n => {
          const parts = n.id.split('.');
          return parseInt(parts[parts.length - 1]);
        })
        .filter(n => !isNaN(n));
      const maxSibling = siblingIds.length > 0 ? Math.max(...siblingIds) : 0;
      newId = `${currentParent.id}.${maxSibling + 1}`;
    }

    const profile: ClinicalProfile = {
      atc: formAtc.trim() || undefined,
      indications: formIndications.trim() || undefined,
      mechanism: formMechanism.trim() || undefined,
      pharmacokinetics: formPk.trim() || undefined,
      pearls: formPearls.trim() || undefined,
      contraindications: formContraindications.trim() || undefined,
      dosageForms: formDosageForms.trim() || undefined,
      monitoring: formMonitoring.trim() || undefined,
    };

    const hasClinicalFields = Object.values(profile).some(v => v !== undefined);
    const desc = hasClinicalFields ? JSON.stringify(profile) : null;

    const newNode: TaxonomyNode = {
      id: newId,
      parentId: currentParent ? currentParent.id : null,
      name: formName.trim(),
      description: desc
    };

    persistNodes([...nodes, newNode]);
    setShowAddDialog(false);
    setShowSaveToast(true);
    setTimeout(() => setShowSaveToast(false), 2500);
  };

  // Delete node
  const handleDeleteNode = () => {
    if (!deletingNode) return;
    const toDeleteIds = new Set<string>();
    
    function collectDescendants(id: string) {
      toDeleteIds.add(id);
      nodes.filter(n => n.parentId === id).forEach(child => collectDescendants(child.id));
    }
    collectDescendants(deletingNode.id);

    persistNodes(nodes.filter(n => !toDeleteIds.has(n.id)));
    if (selectedSubstance && toDeleteIds.has(selectedSubstance.id)) {
      setSelectedSubstance(null);
    }
    setDeletingNode(null);
    setShowSaveToast(true);
    setTimeout(() => setShowSaveToast(false), 2500);
  };

  // Ancestor tree path builder for breadcrumbs
  const getAncestors = (nodeId: string): TaxonomyNode[] => {
    const list: TaxonomyNode[] = [];
    let curr = nodes.find(n => n.id === nodeId);
    while (curr && curr.parentId) {
      const parent = nodes.find(n => n.id === curr?.parentId);
      if (parent) {
        list.unshift(parent);
        curr = parent;
      } else {
        break;
      }
    }
    return list;
  };

  const getCategoryIcon = (id: string, isLeaf: boolean) => {
    if (isLeaf) return <Pill className="w-5 h-5 text-teal-700" />;
    switch (id) {
      case "1": return <Pill className="w-5 h-5 text-emerald-700" />;
      case "2": return <Syringe className="w-5 h-5 text-blue-700" />;
      case "3": return <Microscope className="w-5 h-5 text-purple-700" />;
      case "4": return <Stethoscope className="w-5 h-5 text-cyan-700" />;
      case "5": return <Bandage className="w-5 h-5 text-amber-700" />;
      case "6": return <ShieldAlert className="w-5 h-5 text-red-700" />;
      case "7": return <Baby className="w-5 h-5 text-pink-700" />;
      case "8": return <Sparkles className="w-5 h-5 text-indigo-700" />;
      case "9": return <Apple className="w-5 h-5 text-green-700" />;
      case "10": return <Building2 className="w-5 h-5 text-stone-700" />;
      default: return <Layers className="w-5 h-5 text-emerald-700" />;
    }
  };

  // Facility name save
  const handleSaveFacility = () => {
    const trimmed = facilityDraft.trim() || 'SamilliMed Medical Centre';
    setFacilityName(trimmed);
    localStorage.setItem('samillimed_facility_name', trimmed);
    setIsEditingFacility(false);
  };

  return (
    <div className="min-h-screen bg-[#F5EFE6] text-[#23201D] flex flex-col font-sans select-none antialiased">
      {/* ============================================================== */}
      {/* 1. SEAMLESS TOP HEADER BAR ("Products Database")              */}
      {/* ============================================================== */}
      <header className="sticky top-0 z-40 bg-[#F5EFE6]/95 backdrop-blur-md border-b border-[#E5DFD5] px-4 py-3 sm:px-6">
        <div className="max-w-6xl mx-auto flex items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            {currentView === 'products' && navStack.length > 0 ? (
              <button
                onClick={() => setNavStack(prev => prev.slice(0, -1))}
                className="p-2 rounded-xl hover:bg-[#EAE2D3] text-[#23201D] transition-colors flex items-center gap-1.5 text-xs font-semibold"
                title="Go back one level"
              >
                <ArrowLeft className="w-4 h-4" />
                <span className="hidden sm:inline">Back</span>
              </button>
            ) : null}

            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-lg sm:text-xl font-bold tracking-tight text-[#23201D]">
                  Products Database
                </h1>
                <span className="text-[11px] font-semibold uppercase tracking-wider px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800 border border-emerald-200">
                  Clinical V2.1
                </span>
              </div>
              <p className="text-xs text-[#6B655D]">
                Reference Taxonomy • 19 Clinical Pharmacology Branches • Student-Grade Dossiers
              </p>
            </div>
          </div>

          {/* Action Tools: Search, Export, Reset */}
          <div className="flex items-center gap-2">
            <button
              onClick={() => setIsSearchOpen(!isSearchOpen)}
              className={`p-2 rounded-xl border transition-all ${
                isSearchOpen
                  ? 'bg-emerald-800 text-white border-emerald-900 shadow-xs'
                  : 'bg-[#FAF7F2] hover:bg-white text-[#23201D] border-[#E5DFD5]'
              }`}
              title="Search reference taxonomy & clinical notes"
            >
              <Search className="w-4 h-4" />
            </button>

            <button
              onClick={handleExportDatabase}
              className="p-2 rounded-xl border bg-[#FAF7F2] hover:bg-white text-[#23201D] border-[#E5DFD5] transition-all hidden sm:flex items-center gap-1.5 text-xs font-semibold"
              title="Export database as JSON"
            >
              <Download className="w-4 h-4 text-emerald-800" />
              <span>Export</span>
            </button>

            <button
              onClick={handleResetToDefault}
              className="p-2 rounded-xl border bg-[#FAF7F2] hover:bg-white text-[#23201D] border-[#E5DFD5] transition-all hidden sm:flex items-center gap-1.5 text-xs font-semibold"
              title="Reset database to WHO/SamilliMed reference"
            >
              <RotateCcw className="w-3.5 h-3.5 text-stone-600" />
              <span>Reset</span>
            </button>

            <div className="h-6 w-px bg-[#E5DFD5] mx-1" />

            <button
              onClick={() => setShowSplash(true)}
              className="p-2 rounded-xl border bg-[#FAF7F2] hover:bg-white text-[#23201D] border-[#E5DFD5] transition-all hidden md:flex items-center gap-1.5 text-xs font-semibold"
              title="Replay Android Launch Splash Screen animation"
            >
              <Play className="w-3.5 h-3.5 text-emerald-700" />
              <span>Launch Splash</span>
            </button>

            <div className="h-6 w-px bg-[#E5DFD5] mx-1 hidden sm:block" />

            <div className="flex bg-[#EAE2D3] p-1 rounded-xl">
              <button
                onClick={() => setCurrentView('dashboard')}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5 ${
                  currentView === 'dashboard'
                    ? 'bg-white text-emerald-900 shadow-xs font-bold'
                    : 'text-[#6B655D] hover:text-[#23201D]'
                }`}
              >
                <span>Home</span>
              </button>
              <button
                onClick={() => setCurrentView('products')}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5 ${
                  currentView === 'products'
                    ? 'bg-white text-emerald-900 shadow-xs font-bold'
                    : 'text-[#6B655D] hover:text-[#23201D]'
                }`}
              >
                <span>Products Database</span>
              </button>
            </div>
          </div>
        </div>

        {/* SEARCH EXPANSION TRAY */}
        {isSearchOpen && (
          <div className="max-w-6xl mx-auto mt-3 pt-3 border-t border-[#E5DFD5]/70 animate-in fade-in duration-200">
            <div className="relative">
              <Search className="w-4 h-4 absolute left-3 top-3 text-[#8C8479]" />
              <input
                type="text"
                placeholder="Search across 2,212 drugs, classes, mechanisms, ATC codes (e.g. Sevoflurane, Opioids, N01AB)..."
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                className="w-full bg-white border border-[#E5DFD5] rounded-xl pl-9 pr-10 py-2.5 text-sm text-[#23201D] placeholder-[#8C8479] focus:outline-none focus:ring-2 focus:ring-emerald-700 shadow-xs"
                autoFocus
              />
              {searchQuery && (
                <button
                  onClick={() => setSearchQuery('')}
                  className="absolute right-3 top-2.5 text-[#8C8479] hover:text-[#23201D]"
                >
                  <X className="w-4 h-4" />
                </button>
              )}
            </div>
          </div>
        )}
      </header>

      {/* SUCCESS TOAST */}
      {showSaveToast && (
        <div className="fixed bottom-20 right-6 z-50 bg-emerald-900 text-white px-4 py-2.5 rounded-xl shadow-lg flex items-center gap-2 text-xs font-semibold animate-in slide-in-from-bottom duration-200">
          <Check className="w-4 h-4 text-emerald-300" />
          <span>Database updated and saved successfully!</span>
        </div>
      )}

      {/* ============================================================== */}
      {/* 2. MAIN BODY CONTENT                                           */}
      {/* ============================================================== */}
      <main className="flex-1 max-w-6xl w-full mx-auto p-4 sm:p-6 flex flex-col gap-5">
        {currentView === 'products' ? (
          <>
            {/* TOP NAVIGATION BAR FOR PRODUCTS VIEW */}
            <div className="flex items-center justify-between gap-3 flex-wrap">
              <button
                onClick={() => {
                  setNavStack([]);
                  setCurrentView('dashboard');
                }}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#FAF7F2] hover:bg-white border border-[#E5DFD5] text-xs font-bold text-emerald-900 shadow-xs transition-all cursor-pointer"
              >
                <ArrowLeft className="w-3.5 h-3.5 text-emerald-700" />
                <span>Return to Home Dashboard</span>
              </button>

              <span className="text-[11px] text-[#6B655D] font-medium hidden sm:inline">
                Products Database • 2,212 Canonical Entities
              </span>
            </div>

            {/* CLINICAL BREADCRUMB TRAIL */}
            {navStack.length > 0 && (
              <nav className="flex items-center gap-1.5 text-xs text-[#6B655D] overflow-x-auto pb-1.5 scrollbar-thin">
                <button
                  onClick={() => setNavStack([])}
                  className="hover:text-emerald-800 bg-[#EBE6DC] hover:bg-[#E2DDD1] px-2.5 py-1 rounded-lg font-medium transition-colors shrink-0"
                >
                  Primary Categories
                </button>
                {navStack.map((crumb, idx) => {
                  const isLast = idx === navStack.length - 1;
                  return (
                    <React.Fragment key={crumb.id}>
                      <ChevronRight className="w-3.5 h-3.5 text-[#8C8479] shrink-0" />
                      <button
                        onClick={() => setNavStack(navStack.slice(0, idx + 1))}
                        className={`px-2.5 py-1 rounded-lg transition-colors shrink-0 ${
                          isLast
                            ? 'font-bold text-[#23201D] bg-[#E5DFD5]'
                            : 'hover:text-emerald-800 bg-[#EBE6DC] hover:bg-[#E2DDD1]'
                        }`}
                      >
                        {crumb.name}
                      </button>
                    </React.Fragment>
                  );
                })}
              </nav>
            )}

            {/* LOADING STATE */}
            {isLoading ? (
              <div className="flex-1 flex flex-col items-center justify-center p-16 text-[#6B655D] gap-3">
                <div className="w-8 h-8 border-3 border-emerald-700 border-t-transparent rounded-full animate-spin" />
                <p className="text-sm font-medium">Loading clinical taxonomy database...</p>
              </div>
            ) : searchQuery ? (
              /* SEARCH RESULTS MODE */
              <div className="flex flex-col gap-2.5">
                <div className="flex items-center justify-between px-1">
                  <p className="text-xs font-semibold uppercase tracking-wider text-[#6B655D]">
                    Matching Clinical Entries ({searchResults.length})
                  </p>
                  <span className="text-[11px] text-[#8C8479]">Click to view segmented dossier</span>
                </div>
                {searchResults.length === 0 ? (
                  <div className="p-8 text-center bg-[#FAF7F2] rounded-2xl border border-[#E5DFD5] text-[#6B655D]">
                    No matching categories or substances found for "{searchQuery}".
                  </div>
                ) : (
                  searchResults.map(result => {
                    const count = childCounts[result.id] || 0;
                    const isLeaf = count === 0;
                    const profile = parseClinicalProfile(result.description);
                    return (
                      <div
                        key={result.id}
                        onClick={() => {
                          if (isLeaf) {
                            setSelectedSubstance(result);
                          } else {
                            const path = getAncestors(result.id);
                            setNavStack([...path, result]);
                            setSearchQuery('');
                            setIsSearchOpen(false);
                          }
                        }}
                        className="flex items-center justify-between p-3.5 bg-[#FAF7F2] hover:bg-white border border-[#E5DFD5] hover:border-emerald-700/50 rounded-xl cursor-pointer transition-all shadow-xs group"
                      >
                        <div className="flex items-center gap-3 min-w-0 flex-1">
                          <div className={`w-9 h-9 rounded-full flex items-center justify-center shrink-0 ${isLeaf ? 'bg-teal-50' : 'bg-emerald-50'}`}>
                            {getCategoryIcon(result.id, isLeaf)}
                          </div>
                          <div className="min-w-0 flex-1">
                            <div className="flex items-center gap-2">
                              <p className="font-semibold text-sm text-[#23201D] group-hover:text-emerald-900 truncate">
                                {result.name}
                              </p>
                              {profile.atc && (
                                <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-emerald-100 text-emerald-800 border border-emerald-200 shrink-0">
                                  {profile.atc}
                                </span>
                              )}
                            </div>
                            {profile.indications ? (
                              <p className="text-xs text-[#6B655D] line-clamp-1 mt-0.5">{profile.indications}</p>
                            ) : result.description ? (
                              <p className="text-xs text-[#6B655D] line-clamp-1 mt-0.5">{result.description}</p>
                            ) : null}
                          </div>
                        </div>

                        <div className="flex items-center gap-2 pl-3">
                          <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-[#EBE6DC] text-[#6B655D] shrink-0">
                            {count > 0 ? `${count} items` : 'Substance'}
                          </span>
                          <ChevronRight className="w-4 h-4 text-[#8C8479] group-hover:text-emerald-800" />
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
            ) : currentChildren.length > 0 ? (
              /* HORIZONTAL BARS LISTED DOWNWARDS (Categorization & Subcategories) */
              <div className="flex flex-col gap-2.5">
                {currentParent && (
                  <div className="mb-2 p-4 bg-[#FAF7F2] border border-[#E5DFD5] rounded-xl flex items-center justify-between">
                    <div>
                      <span className="text-[11px] font-bold uppercase tracking-wider text-emerald-800">
                        Current Clinical Class
                      </span>
                      <h2 className="text-lg font-bold text-[#23201D]">{currentParent.name}</h2>
                      {(() => {
                        const prof = parseClinicalProfile(currentParent.description);
                        return prof.indications ? (
                          <p className="text-xs text-[#6B655D] mt-1 leading-relaxed">{prof.indications}</p>
                        ) : null;
                      })()}
                    </div>
                    <button
                      onClick={() => handleStartEdit(currentParent)}
                      className="p-2 border border-[#E5DFD5] rounded-lg hover:bg-white text-[#6B655D] hover:text-[#23201D] transition-colors shrink-0"
                      title="Edit this category description"
                    >
                      <Edit2 className="w-4 h-4" />
                    </button>
                  </div>
                )}

                <div className="flex items-center justify-between px-1">
                  <p className="text-xs font-semibold uppercase tracking-wider text-[#6B655D]">
                    {currentParent ? `${currentChildren.length} Subcategories / Formulations` : '10 Primary Medicine & Healthcare Categories'}
                  </p>
                  <span className="text-[11px] text-[#8C8479]">Click bar to drill down</span>
                </div>

                {currentChildren.map(cat => {
                  const count = childCounts[cat.id] || 0;
                  const isLeaf = count === 0 && currentParent !== null;
                  const profile = parseClinicalProfile(cat.description);
                  return (
                    <div
                      key={cat.id}
                      className="flex items-center justify-between p-3.5 bg-[#FAF7F2] hover:bg-white border border-[#E5DFD5] hover:border-emerald-700/50 rounded-xl transition-all shadow-xs group"
                    >
                      <div
                        onClick={() => {
                          if (isLeaf) {
                            setSelectedSubstance(cat);
                          } else {
                            setNavStack(prev => [...prev, cat]);
                          }
                        }}
                        className="flex items-center gap-3.5 flex-1 cursor-pointer min-w-0"
                      >
                        <div className={`w-10 h-10 rounded-full flex items-center justify-center shrink-0 ${isLeaf ? 'bg-teal-50' : 'bg-emerald-50'}`}>
                          {getCategoryIcon(cat.id, isLeaf)}
                        </div>

                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="font-semibold text-sm sm:text-base text-[#23201D] group-hover:text-emerald-900 truncate">
                              {cat.name}
                            </span>
                            {profile.atc && (
                              <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-emerald-100 text-emerald-800 border border-emerald-200 shrink-0">
                                {profile.atc}
                              </span>
                            )}
                          </div>
                          {profile.indications ? (
                            <p className="text-xs text-[#6B655D] line-clamp-1 mt-0.5">
                              {profile.indications}
                            </p>
                          ) : cat.description ? (
                            <p className="text-xs text-[#6B655D] line-clamp-1 mt-0.5">
                              {cat.description}
                            </p>
                          ) : null}
                        </div>
                      </div>

                      <div className="flex items-center gap-1.5 pl-3 shrink-0">
                        <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-[#EBE6DC] text-[#6B655D]">
                          {count > 0 ? `${count} items` : 'Substance'}
                        </span>

                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            handleStartEdit(cat);
                          }}
                          className="p-1.5 rounded-lg text-[#8C8479] hover:text-emerald-800 hover:bg-[#EAE2D3] transition-colors"
                          title="Edit clinical dossier / name"
                        >
                          <Edit2 className="w-3.5 h-3.5" />
                        </button>

                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            setDeletingNode(cat);
                          }}
                          className="p-1.5 rounded-lg text-[#8C8479] hover:text-red-700 hover:bg-red-50 transition-colors"
                          title="Delete / Archive"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>

                        <div
                          onClick={() => {
                            if (isLeaf) setSelectedSubstance(cat);
                            else setNavStack(prev => [...prev, cat]);
                          }}
                          className="p-1 text-[#8C8479] group-hover:text-emerald-800 cursor-pointer"
                        >
                          <ChevronRight className="w-4 h-4" />
                        </div>
                      </div>
                    </div>
                  );
                })}

                {/* Add subcategory button */}
                <button
                  onClick={() => {
                    setFormName('');
                    setFormAtc('');
                    setFormIndications('');
                    setFormMechanism('');
                    setFormPk('');
                    setFormPearls('');
                    setFormContraindications('');
                    setFormDosageForms('');
                    setFormMonitoring('');
                    setShowAddDialog(true);
                  }}
                  className="mt-2 w-full py-3.5 px-4 border border-dashed border-emerald-700/40 rounded-xl text-emerald-800 hover:bg-emerald-50/50 font-semibold text-sm flex items-center justify-center gap-2 transition-colors"
                >
                  <Plus className="w-4 h-4" />
                  {currentParent ? `Add Subcategory / Substance to ${currentParent.name}` : 'Add New Primary Category'}
                </button>
              </div>
            ) : (
              /* ========================================================== */
              /* 3. TERMINAL LEAF: SEGMENTED TABBED CLINICAL DOSSIER VIEW   */
              /* ========================================================== */
              currentParent && (
                <div className="bg-[#FAF7F2] border border-[#E5DFD5] rounded-2xl p-5 sm:p-7 shadow-xs flex flex-col gap-6">
                  {/* HEADER CARD */}
                  <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4 pb-4 border-b border-[#E5DFD5]">
                    <div className="flex items-start gap-3.5">
                      <div className="w-12 h-12 rounded-xl bg-teal-100 flex items-center justify-center text-teal-800 shrink-0">
                        <Pill className="w-6 h-6" />
                      </div>
                      <div>
                        <div className="flex flex-wrap items-center gap-2">
                          <h2 className="text-xl sm:text-2xl font-bold text-[#23201D] tracking-tight">
                            {currentParent.name}
                          </h2>
                          {(() => {
                            const prof = parseClinicalProfile(currentParent.description);
                            return prof.atc ? (
                              <span className="text-xs font-mono px-2 py-0.5 rounded-md bg-emerald-100 text-emerald-800 font-semibold border border-emerald-200">
                                ATC: {prof.atc}
                              </span>
                            ) : null;
                          })()}
                        </div>
                        <div className="flex items-center gap-2 mt-1">
                          <span className="text-xs font-semibold px-2 py-0.5 rounded bg-teal-100 text-teal-800">
                            Canonical Active Substance
                          </span>
                          <span className="text-xs font-mono text-[#8C8479]">ID: {currentParent.id}</span>
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center gap-2 shrink-0">
                      <button
                        onClick={() => handleStartEdit(currentParent)}
                        className="px-3 py-2 border border-emerald-700/30 rounded-xl bg-white hover:bg-emerald-50 text-emerald-800 text-xs font-semibold flex items-center gap-1.5 transition-colors shadow-xs"
                      >
                        <Edit2 className="w-3.5 h-3.5" />
                        <span>Edit Clinical Dossier</span>
                      </button>
                    </div>
                  </div>

                  {/* SEGMENTED TAB BAR */}
                  {(() => {
                    const prof = parseClinicalProfile(currentParent.description);
                    return (
                      <div className="flex flex-col gap-4">
                        <div className="flex items-center gap-1.5 bg-[#EAE2D3] p-1.5 rounded-xl overflow-x-auto scrollbar-thin">
                          <button
                            onClick={() => setActiveTab('indications')}
                            className={`flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-xs font-semibold transition-all shrink-0 ${
                              activeTab === 'indications'
                                ? 'bg-white text-emerald-900 shadow-xs'
                                : 'text-[#6B655D] hover:text-[#23201D]'
                            }`}
                          >
                            <BookOpen className="w-3.5 h-3.5" />
                            <span>Indications & Role</span>
                          </button>

                          <button
                            onClick={() => setActiveTab('mechanism')}
                            className={`flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-xs font-semibold transition-all shrink-0 ${
                              activeTab === 'mechanism'
                                ? 'bg-white text-emerald-900 shadow-xs'
                                : 'text-[#6B655D] hover:text-[#23201D]'
                            }`}
                          >
                            <Activity className="w-3.5 h-3.5" />
                            <span>Mechanism & PK</span>
                          </button>

                          <button
                            onClick={() => setActiveTab('pearls')}
                            className={`flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-xs font-semibold transition-all shrink-0 ${
                              activeTab === 'pearls'
                                ? 'bg-white text-amber-900 shadow-xs'
                                : 'text-[#6B655D] hover:text-[#23201D]'
                            }`}
                          >
                            <AlertTriangle className="w-3.5 h-3.5 text-amber-700" />
                            <span>Pearls & Safety Red Flags</span>
                          </button>

                          <button
                            onClick={() => setActiveTab('dosage')}
                            className={`flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-xs font-semibold transition-all shrink-0 ${
                              activeTab === 'dosage'
                                ? 'bg-white text-emerald-900 shadow-xs'
                                : 'text-[#6B655D] hover:text-[#23201D]'
                            }`}
                          >
                            <HeartPulse className="w-3.5 h-3.5" />
                            <span>Formulations & Monitoring</span>
                          </button>

                          <button
                            onClick={() => setActiveTab('all')}
                            className={`flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-xs font-semibold transition-all shrink-0 ${
                              activeTab === 'all'
                                ? 'bg-white text-stone-900 shadow-xs'
                                : 'text-[#6B655D] hover:text-[#23201D]'
                            }`}
                          >
                            <Layers className="w-3.5 h-3.5" />
                            <span>Full Dossier</span>
                          </button>
                        </div>

                        {/* TAB CONTENT PANELS */}
                        <div className="flex flex-col gap-4">
                          {/* 1. INDICATIONS & ROLE */}
                          {(activeTab === 'indications' || activeTab === 'all') && (
                            <div className="bg-white p-4.5 rounded-xl border border-[#E5DFD5] flex flex-col gap-2">
                              <div className="flex items-center gap-2 text-emerald-800">
                                <BookOpen className="w-4 h-4" />
                                <h3 className="text-xs font-bold uppercase tracking-wider">
                                  Primary Clinical Indications & EML Status
                                </h3>
                              </div>
                              <p className="text-sm text-[#23201D] leading-relaxed">
                                {prof.indications || "Standard WHO therapeutic indications apply. Full clinical indications will be populated as local formulary evolves."}
                              </p>
                            </div>
                          )}

                          {/* 2. MECHANISM & PHARMACOKINETICS */}
                          {(activeTab === 'mechanism' || activeTab === 'all') && (
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                              <div className="bg-white p-4.5 rounded-xl border border-[#E5DFD5] flex flex-col gap-2">
                                <div className="flex items-center gap-2 text-blue-800">
                                  <Activity className="w-4 h-4" />
                                  <h3 className="text-xs font-bold uppercase tracking-wider">
                                    Mechanism of Action & Receptors
                                  </h3>
                                </div>
                                <p className="text-sm text-[#23201D] leading-relaxed">
                                  {prof.mechanism || "Modulates target physiological pathways specific to this pharmacological class."}
                                </p>
                              </div>

                              <div className="bg-white p-4.5 rounded-xl border border-[#E5DFD5] flex flex-col gap-2">
                                <div className="flex items-center gap-2 text-indigo-800">
                                  <Clock className="w-4 h-4" />
                                  <h3 className="text-xs font-bold uppercase tracking-wider">
                                    Pharmacokinetics & Clearance
                                  </h3>
                                </div>
                                <p className="text-sm text-[#23201D] leading-relaxed">
                                  {prof.pharmacokinetics || "Standard absorption, hepatic metabolism, and renal clearance apply."}
                                </p>
                              </div>
                            </div>
                          )}

                          {/* 3. PEARLS & RED FLAGS */}
                          {(activeTab === 'pearls' || activeTab === 'all') && (
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                              <div className="bg-amber-50/70 p-4.5 rounded-xl border border-amber-200/80 flex flex-col gap-2">
                                <div className="flex items-center gap-2 text-amber-900">
                                  <AlertTriangle className="w-4 h-4 text-amber-700" />
                                  <h3 className="text-xs font-bold uppercase tracking-wider">
                                    Clinical Pearls (For Medical Students & Trainees)
                                  </h3>
                                </div>
                                <p className="text-sm text-[#23201D] leading-relaxed">
                                  {prof.pearls || "Verify patient allergy history, organ clearance, and potential drug-drug interactions prior to administration."}
                                </p>
                              </div>

                              <div className="bg-rose-50/70 p-4.5 rounded-xl border border-rose-200/80 flex flex-col gap-2">
                                <div className="flex items-center gap-2 text-rose-900">
                                  <ShieldAlert className="w-4 h-4 text-rose-700" />
                                  <h3 className="text-xs font-bold uppercase tracking-wider">
                                    Contraindications & Black Box Warnings
                                  </h3>
                                </div>
                                <p className="text-sm text-[#23201D] leading-relaxed">
                                  {prof.contraindications || "Known hypersensitivity to active substance or class. Severe decompensated organ failure."}
                                </p>
                              </div>
                            </div>
                          )}

                          {/* 4. FORMULATIONS & MONITORING */}
                          {(activeTab === 'dosage' || activeTab === 'all') && (
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                              <div className="bg-white p-4.5 rounded-xl border border-[#E5DFD5] flex flex-col gap-2">
                                <div className="flex items-center gap-2 text-teal-800">
                                  <Pill className="w-4 h-4" />
                                  <h3 className="text-xs font-bold uppercase tracking-wider">
                                    Approved Formulations & Routes
                                  </h3>
                                </div>
                                <p className="text-sm text-[#23201D] leading-relaxed">
                                  {prof.dosageForms || "Standard pharmaceutical formulations available in clinical formulary."}
                                </p>
                              </div>

                              <div className="bg-white p-4.5 rounded-xl border border-[#E5DFD5] flex flex-col gap-2">
                                <div className="flex items-center gap-2 text-emerald-800">
                                  <HeartPulse className="w-4 h-4" />
                                  <h3 className="text-xs font-bold uppercase tracking-wider">
                                    Clinical & Laboratory Monitoring
                                  </h3>
                                </div>
                                <p className="text-sm text-[#23201D] leading-relaxed">
                                  {prof.monitoring || "Routine vital signs, symptomatic response, and renal/hepatic laboratory screening."}
                                </p>
                              </div>
                            </div>
                          )}
                        </div>
                      </div>
                    );
                  })()}

                  {/* BOTTOM ACTION BAR */}
                  <div className="pt-4 border-t border-[#E5DFD5] flex items-center justify-between">
                    <button
                      onClick={() => setNavStack(prev => prev.slice(0, -1))}
                      className="px-4 py-2 rounded-xl text-xs font-semibold bg-[#EBE6DC] hover:bg-[#E2DDD1] text-[#23201D] transition-colors"
                    >
                      ← Back to Parent Category
                    </button>

                    <button
                      onClick={() => handleStartEdit(currentParent)}
                      className="px-4 py-2 rounded-xl text-xs font-semibold bg-emerald-800 hover:bg-emerald-900 text-white flex items-center gap-1.5 transition-colors shadow-xs"
                    >
                      <Edit2 className="w-3.5 h-3.5" />
                      <span>Edit Substance Profile</span>
                    </button>
                  </div>
                </div>
              )
            )}
          </>
        ) : (
          /* ============================================================== */
          /* 4. DASHBOARD SCREEN (Authoritative Visual Reference)           */
          /* ============================================================== */
          <div className="flex flex-col gap-6 pb-24 relative max-w-4xl mx-auto w-full">
            {/* 1. AUTHORITATIVE HEADER CARD */}
            <div className="bg-[#FAF7EF] border border-[#EBE6DC] rounded-3xl p-4 sm:p-5 shadow-sm relative overflow-hidden flex items-center justify-between">
              {/* Decorative 3D Spheres peeking behind facility selector */}
              <div className="absolute right-2 sm:right-6 top-1/2 -translate-y-1/2 flex items-center pointer-events-none -z-0 opacity-90">
                <div
                  className="w-12 h-12 rounded-full -mr-3"
                  style={{
                    background: 'radial-gradient(circle at 35% 35%, #FFF 0%, #FFD59E 30%, #D4A05B 70%, #9E6520 100%)',
                    boxShadow: '0 8px 16px rgba(158, 101, 32, 0.25)'
                  }}
                />
                <div
                  className="w-16 h-16 rounded-full"
                  style={{
                    background: 'radial-gradient(circle at 32% 30%, #FFF 0%, #8BB79B 25%, #4A6B53 65%, #244A38 100%)',
                    boxShadow: '0 10px 20px rgba(36, 74, 56, 0.30)'
                  }}
                />
                <div
                  className="w-8 h-8 rounded-full -ml-3 mt-4"
                  style={{
                    background: 'radial-gradient(circle at 40% 35%, #FFF 0%, #F5F5F5 50%, #C0C0C0 100%)',
                    boxShadow: '0 4px 8px rgba(0, 0, 0, 0.15)'
                  }}
                />
              </div>

              {/* Left Brand Area */}
              <div className="flex items-center gap-3.5 relative z-10">
                {/* Cross Emblem with Leaf Motif */}
                <div className="relative w-12 h-12 flex items-center justify-center shrink-0">
                  <div className="w-11 h-11 bg-[#244A38] rounded-xl flex items-center justify-center shadow-xs">
                    <div className="relative w-7 h-7 flex items-center justify-center">
                      <div className="absolute w-2.5 h-7 bg-white/90 rounded-xs" />
                      <div className="absolute w-7 h-2.5 bg-white/90 rounded-xs" />
                      <div className="absolute w-4 h-4 bg-[#244A38] rounded-tl-full rounded-br-full transform rotate-45" />
                    </div>
                  </div>
                </div>

                <div>
                  <h1 className="text-2xl sm:text-3xl font-extrabold text-[#1A2C23] tracking-tight leading-tight">
                    SamilliMed
                  </h1>
                  <p className="text-xs italic text-[#5C7A65] font-medium tracking-wide">
                    Better Health • Brighter Tomorrow
                  </p>
                </div>
              </div>

              {/* Right Facility Selector Pill */}
              <div className="relative z-10">
                {isEditingFacility ? (
                  <div className="flex items-center gap-2 bg-white px-3 py-1.5 rounded-2xl border border-emerald-700 shadow-sm">
                    <input
                      type="text"
                      value={facilityDraft}
                      onChange={e => setFacilityDraft(e.target.value)}
                      className="text-xs font-bold text-[#1A2C23] bg-transparent focus:outline-none w-36"
                      autoFocus
                    />
                    <button
                      onClick={handleSaveFacility}
                      className="px-2 py-0.5 bg-[#244A38] text-white rounded-lg text-[10px] font-bold"
                    >
                      Save
                    </button>
                    <button
                      onClick={() => setIsEditingFacility(false)}
                      className="text-[10px] text-[#6B655D]"
                    >
                      Cancel
                    </button>
                  </div>
                ) : (
                  <button
                    onClick={() => {
                      setFacilityDraft(facilityName);
                      setIsEditingFacility(true);
                    }}
                    className="flex items-center gap-2.5 bg-white/95 hover:bg-white px-3.5 py-1.5 rounded-2xl border border-[#E0DDD5] shadow-xs transition-all cursor-pointer backdrop-blur-xs"
                    title="Tap to rename facility"
                  >
                    <Building2 className="w-4 h-4 text-[#244A38] shrink-0" />
                    <div className="text-left">
                      <span className="block text-[9px] font-medium text-stone-500 uppercase leading-none">
                        Facility
                      </span>
                      <span className="text-xs font-bold text-[#1A2C23] leading-tight truncate block max-w-[130px] sm:max-w-[180px]">
                        {facilityName}
                      </span>
                    </div>
                    <ChevronRight className="w-3.5 h-3.5 text-stone-700 transform rotate-90 shrink-0" />
                  </button>
                )}
              </div>
            </div>

            {/* 2. THREE-COLUMN GRID: 9 TILES IN EXACT REQUIRED ORDER */}
            <div className="grid grid-cols-3 gap-3 sm:gap-4">
              {/* Row 1, Col 1: Stock Adjustments */}
              <div
                onClick={() => setActiveDashboardAction('adjustments')}
                className="bg-[#FAF7EF] hover:bg-white border border-[#EBE6DC] hover:border-[#D4A05B] rounded-3xl p-3 sm:p-4 transition-all duration-200 shadow-xs hover:shadow-md cursor-pointer flex flex-col items-center justify-between min-h-[115px] sm:min-h-[135px] text-center group"
              >
                <div className="w-11 h-11 rounded-full bg-[#244A38] text-white flex items-center justify-center group-hover:scale-105 transition-transform shadow-xs">
                  <RefreshCw className="w-5 h-5" />
                </div>
                <span className="text-xs font-bold text-[#1A2C23] leading-snug">
                  Stock<br />Adjustments
                </span>
                <ChevronRight className="w-3.5 h-3.5 text-[#A39E93]" />
              </div>

              {/* Row 1, Col 2: Goods Receiving */}
              <div
                onClick={() => setActiveDashboardAction('receiving')}
                className="bg-[#FAF7EF] hover:bg-white border border-[#EBE6DC] hover:border-[#D4A05B] rounded-3xl p-3 sm:p-4 transition-all duration-200 shadow-xs hover:shadow-md cursor-pointer flex flex-col items-center justify-between min-h-[115px] sm:min-h-[135px] text-center group"
              >
                <div className="w-11 h-11 rounded-full bg-[#D4A05B] text-white flex items-center justify-center group-hover:scale-105 transition-transform shadow-xs">
                  <Package className="w-5 h-5" />
                </div>
                <span className="text-xs font-bold text-[#1A2C23] leading-snug">
                  Goods<br />Receiving
                </span>
                <ChevronRight className="w-3.5 h-3.5 text-[#A39E93]" />
              </div>

              {/* Row 1, Col 3: Dispensing */}
              <div
                onClick={() => setActiveDashboardAction('dispensing')}
                className="bg-[#FAF7EF] hover:bg-white border border-[#EBE6DC] hover:border-[#D4A05B] rounded-3xl p-3 sm:p-4 transition-all duration-200 shadow-xs hover:shadow-md cursor-pointer flex flex-col items-center justify-between min-h-[115px] sm:min-h-[135px] text-center group"
              >
                <div className="w-11 h-11 rounded-full bg-[#244A38] text-white flex items-center justify-center group-hover:scale-105 transition-transform shadow-xs">
                  <Pill className="w-5 h-5" />
                </div>
                <span className="text-xs font-bold text-[#1A2C23] leading-snug">
                  Dispensing
                </span>
                <ChevronRight className="w-3.5 h-3.5 text-[#A39E93]" />
              </div>

              {/* Row 2, Col 1: Inventory */}
              <div
                onClick={() => setActiveDashboardAction('inventory')}
                className="bg-[#FAF7EF] hover:bg-white border border-[#EBE6DC] hover:border-[#D4A05B] rounded-3xl p-3 sm:p-4 transition-all duration-200 shadow-xs hover:shadow-md cursor-pointer flex flex-col items-center justify-between min-h-[115px] sm:min-h-[135px] text-center group"
              >
                <div className="w-11 h-11 rounded-full bg-[#244A38] text-white flex items-center justify-center group-hover:scale-105 transition-transform shadow-xs">
                  <Layers className="w-5 h-5" />
                </div>
                <span className="text-xs font-bold text-[#1A2C23] leading-snug">
                  Inventory
                </span>
                <ChevronRight className="w-3.5 h-3.5 text-[#A39E93]" />
              </div>

              {/* Row 2, Col 2: Drug Index */}
              <div
                onClick={() => setCurrentView('products')}
                className="bg-[#FAF7EF] hover:bg-white border-2 border-emerald-700/40 hover:border-emerald-700 rounded-3xl p-3 sm:p-4 transition-all duration-200 shadow-xs hover:shadow-md cursor-pointer flex flex-col items-center justify-between min-h-[115px] sm:min-h-[135px] text-center group"
              >
                <div className="w-11 h-11 rounded-full bg-[#D4A05B] text-white flex items-center justify-center group-hover:scale-105 transition-transform shadow-xs">
                  <Syringe className="w-5 h-5" />
                </div>
                <span className="text-xs font-bold text-emerald-950 leading-snug">
                  Drug Index
                </span>
                <ChevronRight className="w-3.5 h-3.5 text-emerald-800" />
              </div>

              {/* Row 2, Col 3: Goods Registration */}
              <div
                onClick={() => setShowAddDialog(true)}
                className="bg-[#FAF7EF] hover:bg-white border border-[#EBE6DC] hover:border-[#D4A05B] rounded-3xl p-3 sm:p-4 transition-all duration-200 shadow-xs hover:shadow-md cursor-pointer flex flex-col items-center justify-between min-h-[115px] sm:min-h-[135px] text-center group"
              >
                <div className="w-11 h-11 rounded-full bg-[#244A38] text-white flex items-center justify-center group-hover:scale-105 transition-transform shadow-xs">
                  <FileText className="w-5 h-5" />
                </div>
                <span className="text-xs font-bold text-[#1A2C23] leading-snug">
                  Goods<br />Registration
                </span>
                <ChevronRight className="w-3.5 h-3.5 text-[#A39E93]" />
              </div>

              {/* Row 3, Col 1: Reports */}
              <div
                onClick={() => setActiveDashboardAction('reports')}
                className="bg-[#FAF7EF] hover:bg-white border border-[#EBE6DC] hover:border-[#D4A05B] rounded-3xl p-3 sm:p-4 transition-all duration-200 shadow-xs hover:shadow-md cursor-pointer flex flex-col items-center justify-between min-h-[115px] sm:min-h-[135px] text-center group"
              >
                <div className="w-11 h-11 rounded-full bg-[#D4A05B] text-white flex items-center justify-center group-hover:scale-105 transition-transform shadow-xs">
                  <BarChart3 className="w-5 h-5" />
                </div>
                <span className="text-xs font-bold text-[#1A2C23] leading-snug">
                  Reports
                </span>
                <ChevronRight className="w-3.5 h-3.5 text-[#A39E93]" />
              </div>

              {/* Row 3, Col 2: Suppliers */}
              <div
                onClick={() => setActiveDashboardAction('suppliers')}
                className="bg-[#FAF7EF] hover:bg-white border border-[#EBE6DC] hover:border-[#D4A05B] rounded-3xl p-3 sm:p-4 transition-all duration-200 shadow-xs hover:shadow-md cursor-pointer flex flex-col items-center justify-between min-h-[115px] sm:min-h-[135px] text-center group"
              >
                <div className="w-11 h-11 rounded-full bg-[#244A38] text-white flex items-center justify-center group-hover:scale-105 transition-transform shadow-xs">
                  <Building2 className="w-5 h-5" />
                </div>
                <span className="text-xs font-bold text-[#1A2C23] leading-snug">
                  Suppliers
                </span>
                <ChevronRight className="w-3.5 h-3.5 text-[#A39E93]" />
              </div>

              {/* Row 3, Col 3: Services Index */}
              <div
                onClick={() => setActiveDashboardAction('services')}
                className="bg-[#FAF7EF] hover:bg-white border border-[#EBE6DC] hover:border-[#D4A05B] rounded-3xl p-3 sm:p-4 transition-all duration-200 shadow-xs hover:shadow-md cursor-pointer flex flex-col items-center justify-between min-h-[115px] sm:min-h-[135px] text-center group"
              >
                <div className="w-11 h-11 rounded-2xl bg-[#244A38] text-[#FFD59E] flex items-center justify-center group-hover:scale-105 transition-transform shadow-xs">
                  <Stethoscope className="w-5 h-5" />
                </div>
                <span className="text-xs font-bold text-[#1A2C23] leading-snug">
                  Services Index
                </span>
                <ChevronRight className="w-3.5 h-3.5 text-[#A39E93]" />
              </div>
            </div>

            {/* 3. INFORMATION CARDS BENEATH THE TILES IN EXACT ORDER */}

            {/* CARD 1: TODAY'S SALES (Dark Forest Green with Area Sparkline) */}
            <div className="bg-gradient-to-b from-[#1E4332] to-[#143324] text-white rounded-3xl p-5 shadow-md flex items-center justify-between gap-4">
              <div className="flex flex-col gap-1.5 flex-1">
                <div className="flex items-center gap-2.5">
                  <div className="w-8 h-8 rounded-full bg-[#335C47] flex items-center justify-center text-white">
                    <Activity className="w-4 h-4" />
                  </div>
                  <div>
                    <h3 className="font-bold text-sm text-white">Today's Sales</h3>
                    <p className="text-[11px] text-[#9ABCA6]">Mon, 13 Oct 2026</p>
                  </div>
                </div>

                <div className="mt-1">
                  <span className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
                    KSh 48,750
                  </span>
                  <p className="text-xs text-[#86EFAC] font-medium mt-0.5">
                    ↑ 12% vs. yesterday
                  </p>
                </div>
              </div>

              {/* Glowing Green Area Sparkline with Timestamps */}
              <div className="flex flex-col items-end w-44 sm:w-56 shrink-0">
                <svg viewBox="0 0 200 60" className="w-full h-14 overflow-visible">
                  <defs>
                    <linearGradient id="salesGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="#4ADE80" stopOpacity="0.4" />
                      <stop offset="100%" stopColor="#4ADE80" stopOpacity="0.0" />
                    </linearGradient>
                  </defs>
                  <path
                    d="M 0 45 Q 30 42 50 36 T 100 28 T 150 24 T 200 12 L 200 60 L 0 60 Z"
                    fill="url(#salesGrad)"
                  />
                  <path
                    d="M 0 45 Q 30 42 50 36 T 100 28 T 150 24 T 200 12"
                    fill="none"
                    stroke="#4ADE80"
                    strokeWidth="2.5"
                    strokeLinecap="round"
                  />
                  <circle cx="200" cy="12" r="4" fill="#86EFAC" />
                  <circle cx="200" cy="12" r="2" fill="#FFFFFF" />
                </svg>
                <div className="flex justify-between w-full text-[9px] text-[#9ABCA6] font-medium mt-1">
                  <span>8am</span>
                  <span>10am</span>
                  <span>12pm</span>
                  <span>2pm</span>
                  <span>4pm</span>
                </div>
              </div>
            </div>

            {/* CARD 2: TODAY'S EXPENSES (Pale Cream with Amber Bar Chart) */}
            <div className="bg-[#FAF7EF] border border-[#EBE6DC] rounded-3xl p-5 shadow-xs flex items-center justify-between gap-4">
              <div className="flex flex-col gap-1.5 flex-1">
                <div className="flex items-center gap-2.5">
                  <div className="w-8 h-8 rounded-full bg-[#D4A05B] flex items-center justify-center text-white">
                    <DollarSign className="w-4 h-4" />
                  </div>
                  <div>
                    <h3 className="font-bold text-sm text-[#1A2C23]">Today's Expenses</h3>
                    <p className="text-[11px] text-stone-500">Mon, 13 Oct 2026</p>
                  </div>
                </div>

                <div className="mt-1">
                  <span className="text-2xl sm:text-3xl font-extrabold text-[#1A2C23] tracking-tight">
                    KSh 12,430
                  </span>
                  <p className="text-xs text-[#5C7A65] font-medium mt-0.5">
                    ↓ 8% vs. yesterday
                  </p>
                </div>
              </div>

              {/* Amber Vertical Bars with Timestamps */}
              <div className="flex flex-col items-end w-44 sm:w-56 shrink-0">
                <div className="flex items-end justify-between w-full h-14 px-1 gap-2">
                  <div className="w-2.5 bg-[#D4A05B]/50 rounded-t-sm h-[30%]" />
                  <div className="w-2.5 bg-[#D4A05B]/70 rounded-t-sm h-[50%]" />
                  <div className="w-2.5 bg-[#D4A05B] rounded-t-sm h-[70%]" />
                  <div className="w-2.5 bg-[#D4A05B]/60 rounded-t-sm h-[55%]" />
                  <div className="w-2.5 bg-[#D4A05B] rounded-t-sm h-[80%]" />
                  <div className="w-2.5 bg-[#D4A05B]/60 rounded-t-sm h-[60%]" />
                  <div className="w-2.5 bg-[#D4A05B] rounded-t-sm h-[95%]" />
                </div>
                <div className="flex justify-between w-full text-[9px] text-stone-500 font-medium mt-1">
                  <span>8am</span>
                  <span>10am</span>
                  <span>12pm</span>
                  <span>2pm</span>
                  <span>4pm</span>
                </div>
              </div>
            </div>

            {/* CARD 3: EXPIRY ALERTS (Pale Warm with Red Alert Badge) */}
            <div
              onClick={() => setActiveDashboardAction('alerts')}
              className="bg-[#FAF7EF] hover:bg-white border border-[#EBE6DC] hover:border-red-300 rounded-3xl p-5 shadow-xs transition-all cursor-pointer flex items-center justify-between"
            >
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-full bg-[#D34538] flex items-center justify-center text-white shrink-0">
                  <AlertTriangle className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="font-bold text-sm text-[#1A2C23]">Expiry Alerts</h3>
                  <p className="text-[11px] text-stone-500">Mon, 13 Oct 2026</p>
                  <p className="text-sm font-bold text-[#1A2C23] mt-0.5">
                    3 items expiring soon
                  </p>
                </div>
              </div>

              <ChevronRight className="w-5 h-5 text-[#A39E93]" />
            </div>

            {/* 4. FIXED BOTTOM NAVIGATION BAR */}
            <div className="fixed bottom-4 left-1/2 -translate-x-1/2 z-40 w-[92%] max-w-md">
              <div className="bg-[#244A38] text-white rounded-full px-6 py-2.5 shadow-2xl border border-emerald-900/60 flex items-center justify-between">
                {/* Settings */}
                <button
                  onClick={() => setActiveDashboardAction('settings')}
                  className="flex flex-col items-center gap-1 text-[#C5D6CC] hover:text-white transition-colors"
                >
                  <Settings className="w-5 h-5" />
                  <span className="text-[11px] font-medium">Settings</span>
                </button>

                {/* Dashboard (Raised prominent green badge) */}
                <div className="flex flex-col items-center -mt-6">
                  <button
                    onClick={() => setCurrentView('dashboard')}
                    className="w-14 h-14 rounded-full bg-[#3B6647] border-2 border-[#528562] shadow-lg flex items-center justify-center text-white hover:scale-105 transition-transform"
                  >
                    <BookOpen className="w-6 h-6" />
                  </button>
                  <span className="text-[11px] font-bold text-white mt-1">Dashboard</span>
                </div>

                {/* Receiving */}
                <button
                  onClick={() => setActiveDashboardAction('receiving')}
                  className="flex flex-col items-center gap-1 text-[#C5D6CC] hover:text-white transition-colors"
                >
                  <Package className="w-5 h-5" />
                  <span className="text-[11px] font-medium">Receiving</span>
                </button>
              </div>
            </div>
          </div>
        )}
      </main>

      {/* ============================================================== */}
      {/* 5. MODALS & DIALOGS (Full Database Editability)               */}
      {/* ============================================================== */}

      {/* EDIT MODAL WITH SEGMENTED TABS */}
      {editingNode && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-[#FAF7F2] border border-[#E5DFD5] rounded-2xl max-w-2xl w-full p-6 shadow-2xl flex flex-col gap-4 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between pb-3 border-b border-[#E5DFD5]">
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-lg bg-emerald-100 flex items-center justify-center text-emerald-800">
                  <Edit2 className="w-4 h-4" />
                </div>
                <div>
                  <h3 className="font-bold text-base text-[#23201D]">Edit Clinical Profile</h3>
                  <span className="text-xs font-mono text-[#6B655D]">ID: {editingNode.id}</span>
                </div>
              </div>
              <button onClick={() => setEditingNode(null)} className="text-[#8C8479] hover:text-[#23201D]">
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* TAB SELECTOR FOR EDITING */}
            <div className="flex items-center gap-1 bg-[#EAE2D3] p-1 rounded-xl text-xs font-semibold">
              <button
                onClick={() => setEditTab('basic')}
                className={`flex-1 py-1.5 rounded-lg transition-all ${
                  editTab === 'basic' ? 'bg-white text-emerald-900 shadow-xs' : 'text-[#6B655D]'
                }`}
              >
                1. Identity & ATC
              </button>
              <button
                onClick={() => setEditTab('clinical')}
                className={`flex-1 py-1.5 rounded-lg transition-all ${
                  editTab === 'clinical' ? 'bg-white text-emerald-900 shadow-xs' : 'text-[#6B655D]'
                }`}
              >
                2. Indications & Role
              </button>
              <button
                onClick={() => setEditTab('safety')}
                className={`flex-1 py-1.5 rounded-lg transition-all ${
                  editTab === 'safety' ? 'bg-white text-amber-900 shadow-xs' : 'text-[#6B655D]'
                }`}
              >
                3. Pearls & Safety
              </button>
              <button
                onClick={() => setEditTab('delivery')}
                className={`flex-1 py-1.5 rounded-lg transition-all ${
                  editTab === 'delivery' ? 'bg-white text-teal-900 shadow-xs' : 'text-[#6B655D]'
                }`}
              >
                4. Dosage & Monitor
              </button>
            </div>

            {/* EDIT TAB CONTENTS */}
            <div className="flex flex-col gap-3 py-1">
              {editTab === 'basic' && (
                <>
                  <div>
                    <label className="text-xs font-bold text-[#6B655D] block mb-1">
                      Substance / Category Name *
                    </label>
                    <input
                      type="text"
                      value={formName}
                      onChange={e => setFormName(e.target.value)}
                      className="w-full bg-white border border-[#E5DFD5] rounded-xl px-3 py-2 text-sm text-[#23201D] focus:outline-none focus:ring-2 focus:ring-emerald-700"
                      autoFocus
                    />
                  </div>

                  <div>
                    <label className="text-xs font-bold text-[#6B655D] block mb-1">
                      WHO ATC Classification Code
                    </label>
                    <input
                      type="text"
                      placeholder="e.g. N01AB08, M01AE01"
                      value={formAtc}
                      onChange={e => setFormAtc(e.target.value)}
                      className="w-full bg-white border border-[#E5DFD5] rounded-xl px-3 py-2 text-sm text-[#23201D] focus:outline-none focus:ring-2 focus:ring-emerald-700 font-mono"
                    />
                  </div>

                  <div>
                    <label className="text-xs font-bold text-[#6B655D] block mb-1">
                      Mechanism of Action & Molecular Targets
                    </label>
                    <textarea
                      value={formMechanism}
                      onChange={e => setFormMechanism(e.target.value)}
                      placeholder="e.g. Positive allosteric modulator of GABA-A receptors..."
                      rows={3}
                      className="w-full bg-white border border-[#E5DFD5] rounded-xl px-3 py-2 text-sm text-[#23201D] focus:outline-none focus:ring-2 focus:ring-emerald-700 resize-none"
                    />
                  </div>
                </>
              )}

              {editTab === 'clinical' && (
                <>
                  <div>
                    <label className="text-xs font-bold text-[#6B655D] block mb-1">
                      Primary Clinical Indications & EML Status
                    </label>
                    <textarea
                      value={formIndications}
                      onChange={e => setFormIndications(e.target.value)}
                      placeholder="e.g. Induction and maintenance of general anesthesia; WHO EML Core..."
                      rows={4}
                      className="w-full bg-white border border-[#E5DFD5] rounded-xl px-3 py-2 text-sm text-[#23201D] focus:outline-none focus:ring-2 focus:ring-emerald-700 resize-none"
                      autoFocus
                    />
                  </div>

                  <div>
                    <label className="text-xs font-bold text-[#6B655D] block mb-1">
                      Pharmacokinetics & Clearance
                    </label>
                    <textarea
                      value={formPk}
                      onChange={e => setFormPk(e.target.value)}
                      placeholder="e.g. MAC 2.05%, Blood-gas partition 0.65, minimal hepatic metabolism..."
                      rows={3}
                      className="w-full bg-white border border-[#E5DFD5] rounded-xl px-3 py-2 text-sm text-[#23201D] focus:outline-none focus:ring-2 focus:ring-emerald-700 resize-none"
                    />
                  </div>
                </>
              )}

              {editTab === 'safety' && (
                <>
                  <div>
                    <label className="text-xs font-bold text-amber-900 block mb-1">
                      Clinical Pearls & High-Yield Bedside Teaching
                    </label>
                    <textarea
                      value={formPearls}
                      onChange={e => setFormPearls(e.target.value)}
                      placeholder="e.g. Non-pungent, sweet odor preferred for pediatric mask induction..."
                      rows={4}
                      className="w-full bg-white border border-amber-300 rounded-xl px-3 py-2 text-sm text-[#23201D] focus:outline-none focus:ring-2 focus:ring-amber-700 resize-none"
                      autoFocus
                    />
                  </div>

                  <div>
                    <label className="text-xs font-bold text-rose-900 block mb-1">
                      Contraindications & Black Box Warnings
                    </label>
                    <textarea
                      value={formContraindications}
                      onChange={e => setFormContraindications(e.target.value)}
                      placeholder="e.g. Known genetic susceptibility to malignant hyperthermia..."
                      rows={3}
                      className="w-full bg-white border border-rose-300 rounded-xl px-3 py-2 text-sm text-[#23201D] focus:outline-none focus:ring-2 focus:ring-rose-700 resize-none"
                    />
                  </div>
                </>
              )}

              {editTab === 'delivery' && (
                <>
                  <div>
                    <label className="text-xs font-bold text-teal-900 block mb-1">
                      Approved Dosage Forms & Formulations
                    </label>
                    <textarea
                      value={formDosageForms}
                      onChange={e => setFormDosageForms(e.target.value)}
                      placeholder="e.g. Volatile liquid for inhalation: 250 mL bottles..."
                      rows={3}
                      className="w-full bg-white border border-teal-300 rounded-xl px-3 py-2 text-sm text-[#23201D] focus:outline-none focus:ring-2 focus:ring-teal-700 resize-none"
                      autoFocus
                    />
                  </div>

                  <div>
                    <label className="text-xs font-bold text-emerald-900 block mb-1">
                      Clinical & Laboratory Monitoring Parameters
                    </label>
                    <textarea
                      value={formMonitoring}
                      onChange={e => setFormMonitoring(e.target.value)}
                      placeholder="e.g. Continuous end-tidal concentration, capnography, ECG, core temperature..."
                      rows={3}
                      className="w-full bg-white border border-emerald-300 rounded-xl px-3 py-2 text-sm text-[#23201D] focus:outline-none focus:ring-2 focus:ring-emerald-700 resize-none"
                    />
                  </div>
                </>
              )}
            </div>

            <div className="flex justify-between items-center pt-3 border-t border-[#E5DFD5]">
              <span className="text-[11px] text-[#8C8479]">Changes persist locally</span>
              <div className="flex gap-2">
                <button
                  onClick={() => setEditingNode(null)}
                  className="px-4 py-2 rounded-xl text-xs font-semibold bg-[#EBE6DC] hover:bg-[#E2DDD1] text-[#23201D]"
                >
                  Cancel
                </button>
                <button
                  onClick={handleSaveEdit}
                  className="px-5 py-2 rounded-xl text-xs font-semibold bg-emerald-800 hover:bg-emerald-900 text-white flex items-center gap-1.5 shadow-xs"
                >
                  <Check className="w-3.5 h-3.5" />
                  <span>Save Changes</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ADD MODAL */}
      {showAddDialog && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-[#FAF7F2] border border-[#E5DFD5] rounded-2xl max-w-xl w-full p-6 shadow-2xl flex flex-col gap-4">
            <div className="flex items-center justify-between pb-3 border-b border-[#E5DFD5]">
              <h3 className="font-bold text-base text-[#23201D]">
                {currentParent ? `Add to ${currentParent.name}` : 'Add Primary Category'}
              </h3>
              <button onClick={() => setShowAddDialog(false)} className="text-[#8C8479] hover:text-[#23201D]">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="flex flex-col gap-3">
              <div>
                <label className="text-xs font-bold text-[#6B655D] block mb-1">
                  Name / Title *
                </label>
                <input
                  type="text"
                  placeholder="e.g. Dexmedetomidine"
                  value={formName}
                  onChange={e => setFormName(e.target.value)}
                  className="w-full bg-white border border-[#E5DFD5] rounded-xl px-3 py-2 text-sm text-[#23201D] focus:outline-none focus:ring-2 focus:ring-emerald-700"
                  autoFocus
                />
              </div>

              <div>
                <label className="text-xs font-bold text-[#6B655D] block mb-1">
                  ATC Code (Optional)
                </label>
                <input
                  type="text"
                  placeholder="e.g. N05CM18"
                  value={formAtc}
                  onChange={e => setFormAtc(e.target.value)}
                  className="w-full bg-white border border-[#E5DFD5] rounded-xl px-3 py-2 text-sm text-[#23201D] focus:outline-none focus:ring-2 focus:ring-emerald-700 font-mono"
                />
              </div>

              <div>
                <label className="text-xs font-bold text-[#6B655D] block mb-1">
                  Primary Indications & Clinical Role
                </label>
                <textarea
                  value={formIndications}
                  onChange={e => setFormIndications(e.target.value)}
                  placeholder="Brief clinical indications and role..."
                  rows={2}
                  className="w-full bg-white border border-[#E5DFD5] rounded-xl px-3 py-2 text-sm text-[#23201D] focus:outline-none focus:ring-2 focus:ring-emerald-700 resize-none"
                />
              </div>

              <div>
                <label className="text-xs font-bold text-[#6B655D] block mb-1">
                  Mechanism of Action
                </label>
                <textarea
                  value={formMechanism}
                  onChange={e => setFormMechanism(e.target.value)}
                  placeholder="Molecular mechanism and pharmacological action..."
                  rows={2}
                  className="w-full bg-white border border-[#E5DFD5] rounded-xl px-3 py-2 text-sm text-[#23201D] focus:outline-none focus:ring-2 focus:ring-emerald-700 resize-none"
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-3 border-t border-[#E5DFD5]">
              <button
                onClick={() => setShowAddDialog(false)}
                className="px-4 py-2 rounded-xl text-xs font-semibold bg-[#EBE6DC] text-[#23201D]"
              >
                Cancel
              </button>
              <button
                onClick={handleAddNode}
                className="px-5 py-2 rounded-xl text-xs font-semibold bg-emerald-800 hover:bg-emerald-900 text-white"
              >
                Create Entry
              </button>
            </div>
          </div>
        </div>
      )}

      {/* DELETE CONFIRMATION DIALOG */}
      {deletingNode && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-[#FAF7F2] border border-[#E5DFD5] rounded-2xl max-w-sm w-full p-6 shadow-2xl flex flex-col gap-4">
            <div className="flex items-center gap-3 text-red-700">
              <div className="w-10 h-10 rounded-xl bg-red-100 flex items-center justify-center">
                <Trash2 className="w-5 h-5" />
              </div>
              <div>
                <h3 className="font-bold text-base text-[#23201D]">Delete Entry</h3>
                <p className="text-xs text-[#6B655D]">{deletingNode.name}</p>
              </div>
            </div>

            <p className="text-xs text-[#23201D] leading-relaxed">
              Are you sure you want to remove this entry? All child subcategories or substances underneath will also be deleted.
            </p>

            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setDeletingNode(null)}
                className="px-4 py-2 rounded-xl text-xs font-semibold bg-[#EBE6DC] text-[#23201D]"
              >
                Cancel
              </button>
              <button
                onClick={handleDeleteNode}
                className="px-4 py-2 rounded-xl text-xs font-semibold bg-red-700 hover:bg-red-800 text-white"
              >
                Confirm Delete
              </button>
            </div>
          </div>
        </div>
      )}

      {/* QUICK SUBSTANCE DETAIL MODAL (WHEN CLICKED FROM LIST) */}
      {selectedSubstance && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-[#FAF7F2] border border-[#E5DFD5] rounded-2xl max-w-2xl w-full p-6 shadow-2xl flex flex-col gap-5 max-h-[90vh] overflow-y-auto">
            <div className="flex items-start justify-between pb-3 border-b border-[#E5DFD5]">
              <div className="flex items-start gap-3">
                <div className="w-10 h-10 rounded-xl bg-teal-100 flex items-center justify-center text-teal-800 shrink-0">
                  <Pill className="w-5 h-5" />
                </div>
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="font-bold text-lg text-[#23201D]">{selectedSubstance.name}</h3>
                    {(() => {
                      const prof = parseClinicalProfile(selectedSubstance.description);
                      return prof.atc ? (
                        <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-emerald-100 text-emerald-800 font-semibold border border-emerald-200">
                          {prof.atc}
                        </span>
                      ) : null;
                    })()}
                  </div>
                  <span className="text-[11px] font-semibold text-teal-800 uppercase tracking-wide">
                    Canonical Active Substance • ID: {selectedSubstance.id}
                  </span>
                </div>
              </div>
              <button onClick={() => setSelectedSubstance(null)} className="text-[#8C8479] hover:text-[#23201D]">
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* TAB SELECTOR FOR MODAL */}
            {(() => {
              const prof = parseClinicalProfile(selectedSubstance.description);
              return (
                <div className="flex flex-col gap-4">
                  <div className="flex items-center gap-1 bg-[#EAE2D3] p-1.5 rounded-xl overflow-x-auto scrollbar-thin text-xs font-semibold">
                    <button
                      onClick={() => setActiveTab('indications')}
                      className={`px-3 py-1.5 rounded-lg transition-all shrink-0 ${
                        activeTab === 'indications' ? 'bg-white text-emerald-900 shadow-xs' : 'text-[#6B655D]'
                      }`}
                    >
                      Indications
                    </button>
                    <button
                      onClick={() => setActiveTab('mechanism')}
                      className={`px-3 py-1.5 rounded-lg transition-all shrink-0 ${
                        activeTab === 'mechanism' ? 'bg-white text-emerald-900 shadow-xs' : 'text-[#6B655D]'
                      }`}
                    >
                      Mechanism & PK
                    </button>
                    <button
                      onClick={() => setActiveTab('pearls')}
                      className={`px-3 py-1.5 rounded-lg transition-all shrink-0 ${
                        activeTab === 'pearls' ? 'bg-white text-amber-900 shadow-xs' : 'text-[#6B655D]'
                      }`}
                    >
                      Pearls & Safety
                    </button>
                    <button
                      onClick={() => setActiveTab('dosage')}
                      className={`px-3 py-1.5 rounded-lg transition-all shrink-0 ${
                        activeTab === 'dosage' ? 'bg-white text-emerald-900 shadow-xs' : 'text-[#6B655D]'
                      }`}
                    >
                      Formulations
                    </button>
                  </div>

                  {/* ACTIVE TAB DISPLAY */}
                  {activeTab === 'indications' && (
                    <div className="bg-white p-4 rounded-xl border border-[#E5DFD5]">
                      <p className="text-xs font-bold text-emerald-800 uppercase tracking-wide mb-1">
                        Clinical Indications & Role
                      </p>
                      <p className="text-xs sm:text-sm text-[#23201D] leading-relaxed">
                        {prof.indications || selectedSubstance.description || "Standard clinical formulation."}
                      </p>
                    </div>
                  )}

                  {activeTab === 'mechanism' && (
                    <div className="flex flex-col gap-3">
                      <div className="bg-white p-4 rounded-xl border border-[#E5DFD5]">
                        <p className="text-xs font-bold text-blue-800 uppercase tracking-wide mb-1">
                          Mechanism of Action
                        </p>
                        <p className="text-xs sm:text-sm text-[#23201D] leading-relaxed">
                          {prof.mechanism || "Modulates targeted physiological receptors."}
                        </p>
                      </div>
                      <div className="bg-white p-4 rounded-xl border border-[#E5DFD5]">
                        <p className="text-xs font-bold text-indigo-800 uppercase tracking-wide mb-1">
                          Pharmacokinetics & Clearance
                        </p>
                        <p className="text-xs sm:text-sm text-[#23201D] leading-relaxed">
                          {prof.pharmacokinetics || "Standard systemic distribution and clearance."}
                        </p>
                      </div>
                    </div>
                  )}

                  {activeTab === 'pearls' && (
                    <div className="flex flex-col gap-3">
                      <div className="bg-amber-50/70 p-4 rounded-xl border border-amber-200">
                        <p className="text-xs font-bold text-amber-900 uppercase tracking-wide mb-1 flex items-center gap-1.5">
                          <AlertTriangle className="w-3.5 h-3.5 text-amber-700" />
                          High-Yield Bedside Pearls
                        </p>
                        <p className="text-xs sm:text-sm text-[#23201D] leading-relaxed">
                          {prof.pearls || "Verify allergies and baseline organ function."}
                        </p>
                      </div>
                      <div className="bg-rose-50/70 p-4 rounded-xl border border-rose-200">
                        <p className="text-xs font-bold text-rose-900 uppercase tracking-wide mb-1 flex items-center gap-1.5">
                          <ShieldAlert className="w-3.5 h-3.5 text-rose-700" />
                          Contraindications & Red Flags
                        </p>
                        <p className="text-xs sm:text-sm text-[#23201D] leading-relaxed">
                          {prof.contraindications || "Known hypersensitivity or acute decompensation."}
                        </p>
                      </div>
                    </div>
                  )}

                  {activeTab === 'dosage' && (
                    <div className="flex flex-col gap-3">
                      <div className="bg-white p-4 rounded-xl border border-[#E5DFD5]">
                        <p className="text-xs font-bold text-teal-800 uppercase tracking-wide mb-1">
                          Dosage Forms & Commercial Formulations
                        </p>
                        <p className="text-xs sm:text-sm text-[#23201D] leading-relaxed">
                          {prof.dosageForms || "Standard pharmaceutical presentations."}
                        </p>
                      </div>
                      <div className="bg-white p-4 rounded-xl border border-[#E5DFD5]">
                        <p className="text-xs font-bold text-emerald-800 uppercase tracking-wide mb-1">
                          Clinical Monitoring Parameters
                        </p>
                        <p className="text-xs sm:text-sm text-[#23201D] leading-relaxed">
                          {prof.monitoring || "Vital signs, symptom resolution, and routine laboratory monitoring."}
                        </p>
                      </div>
                    </div>
                  )}
                </div>
              );
            })()}

            <div className="flex justify-between items-center pt-3 border-t border-[#E5DFD5]">
              <button
                onClick={() => {
                  const node = selectedSubstance;
                  setSelectedSubstance(null);
                  handleStartEdit(node);
                }}
                className="text-xs font-semibold text-emerald-800 hover:underline flex items-center gap-1"
              >
                <Edit2 className="w-3.5 h-3.5" />
                Edit Substance Info
              </button>
              <button
                onClick={() => setSelectedSubstance(null)}
                className="px-5 py-2 rounded-xl text-xs font-semibold bg-emerald-800 hover:bg-emerald-900 text-white"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* LAUNCH SPLASH SCREEN (Matches SamilliMedSplashScreen.kt) */}
      {showSplash && (
        <div className="fixed inset-0 z-50 bg-[#F3EFE6] flex flex-col items-center justify-center p-6 text-center animate-in fade-in duration-300">
          {/* Ambient Lighting Circles */}
          <div className="absolute inset-0 overflow-hidden pointer-events-none">
            <div className="absolute -top-24 -left-24 w-96 h-96 rounded-full bg-emerald-200/30 blur-3xl animate-pulse" />
            <div className="absolute -bottom-24 -right-24 w-96 h-96 rounded-full bg-amber-200/30 blur-3xl animate-pulse" />
          </div>

          <div className="relative z-10 flex flex-col items-center max-w-md w-full">
            {/* Animated Logo Container with Vitality Heartbeat Rings */}
            <div className="relative flex items-center justify-center mb-8">
              {/* Expanding Pulse Ring */}
              <div className="absolute w-36 h-36 rounded-full border-2 border-emerald-600/30 animate-ping opacity-60" />
              <div className="absolute w-28 h-28 rounded-full border-2 border-amber-500/30 animate-pulse" />

              {/* Solid Emblem */}
              <div className="w-20 h-20 rounded-3xl bg-gradient-to-br from-emerald-800 to-teal-950 text-white flex items-center justify-center shadow-2xl border border-emerald-600/40 relative z-10">
                <HeartPulse className="w-10 h-10 text-emerald-300 animate-pulse" />
              </div>
            </div>

            {/* App Title */}
            <h1 className="text-3xl sm:text-4xl font-extrabold text-[#1E231F] tracking-tight">
              SamilliMed Ground
            </h1>

            {/* Subtitles & Offline Indicators */}
            <p className="text-xs font-bold tracking-widest text-emerald-800 uppercase mt-2">
              Clinical Dispensary & Pharmacy Core
            </p>
            <p className="text-[11px] text-[#6B655D] mt-1 font-medium">
              Offline-First SQLite • Zero-Drift FIFO • 2,212 Enriched Entities
            </p>

            {/* Loading Indicator */}
            <div className="flex items-center gap-2 mt-8 text-xs text-[#6B655D] font-medium bg-white/70 px-4 py-2 rounded-full border border-[#E5DFD5]">
              <div className="w-2.5 h-2.5 rounded-full bg-emerald-600 animate-ping" />
              <span>Initializing local database & assets...</span>
            </div>

            {/* Skip Button */}
            <button
              onClick={() => setShowSplash(false)}
              className="mt-6 text-xs font-bold text-emerald-900 hover:text-emerald-950 underline underline-offset-4 cursor-pointer"
            >
              Skip to Dashboard →
            </button>
          </div>
        </div>
      )}

      {/* DASHBOARD ACTION MODALS FOR ALL 8 FEATURES & DOCK */}
      {activeDashboardAction && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-[#FAF7F2] border border-[#E5DFD5] rounded-3xl max-w-lg w-full p-6 shadow-2xl flex flex-col gap-4">
            <div className="flex items-center justify-between pb-3 border-b border-[#E5DFD5]">
              <div className="flex items-center gap-2.5">
                <div className="w-9 h-9 rounded-xl bg-emerald-100 flex items-center justify-center text-emerald-800">
                  {activeDashboardAction === 'receiving' && <Truck className="w-5 h-5" />}
                  {activeDashboardAction === 'dispensing' && <Pill className="w-5 h-5" />}
                  {activeDashboardAction === 'inventory' && <Package className="w-5 h-5" />}
                  {activeDashboardAction === 'alerts' && <AlertOctagon className="w-5 h-5 text-rose-700" />}
                  {activeDashboardAction === 'reports' && <BarChart3 className="w-5 h-5" />}
                  {activeDashboardAction === 'suppliers' && <Building2 className="w-5 h-5" />}
                  {activeDashboardAction === 'adjustments' && <RefreshCw className="w-5 h-5" />}
                  {activeDashboardAction === 'scan' && <QrCode className="w-5 h-5" />}
                  {activeDashboardAction === 'vitality' && <Heart className="w-5 h-5 text-rose-600" />}
                  {activeDashboardAction === 'settings' && <Settings className="w-5 h-5" />}
                  {activeDashboardAction === 'price' && <DollarSign className="w-5 h-5" />}
                </div>
                <div>
                  <h3 className="font-bold text-base text-[#23201D]">
                    {activeDashboardAction === 'receiving' && 'Goods Receiving Intake'}
                    {activeDashboardAction === 'dispensing' && 'Prescription Dispensing'}
                    {activeDashboardAction === 'inventory' && 'Inventory Valuation & Stocks'}
                    {activeDashboardAction === 'alerts' && 'Expiry Alerts & FEFO Watch'}
                    {activeDashboardAction === 'reports' && 'Facility Financial Reports'}
                    {activeDashboardAction === 'suppliers' && 'Suppliers & Vendors Directory'}
                    {activeDashboardAction === 'adjustments' && 'Stock Adjustments & FIFO Audit'}
                    {activeDashboardAction === 'scan' && 'Barcode / QR Scanner'}
                    {activeDashboardAction === 'vitality' && 'System Health & Zero-Drift Vitality'}
                    {activeDashboardAction === 'settings' && 'Facility & App Settings'}
                    {activeDashboardAction === 'price' && 'Batch Price & Unit Configuration'}
                  </h3>
                  <p className="text-[11px] text-[#6B655D]">{facilityName}</p>
                </div>
              </div>
              <button onClick={() => setActiveDashboardAction(null)} className="text-[#8C8479] hover:text-[#23201D]">
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* ACTION SPECIFIC CONTENT */}
            {activeDashboardAction === 'receiving' && (
              <div className="flex flex-col gap-3 text-xs">
                <p className="text-[#6B655D] leading-relaxed">
                  Record new supplier deliveries, verify physical lot numbers, and automatically create immutable FIFO cost layers.
                </p>
                <div className="bg-white p-3.5 rounded-2xl border border-[#E5DFD5] flex flex-col gap-2">
                  <div className="flex justify-between font-semibold text-[#23201D]">
                    <span>Linked Taxonomy</span>
                    <span className="text-emerald-800">1,801 Canonical Entities</span>
                  </div>
                  <div className="flex justify-between text-[#6B655D]">
                    <span>Intake Mode</span>
                    <span>Direct Barcode / Camera Scan</span>
                  </div>
                  <div className="flex justify-between text-[#6B655D]">
                    <span>Ledger Integrity</span>
                    <span>Zero-Drift Atomic Persistence</span>
                  </div>
                </div>
              </div>
            )}

            {activeDashboardAction === 'dispensing' && (
              <div className="flex flex-col gap-3 text-xs">
                <p className="text-[#6B655D] leading-relaxed">
                  Fulfill prescriptions with automated First-Expiring, First-Out (FEFO) batch deduction to minimize wastage.
                </p>
                <div className="bg-white p-3.5 rounded-2xl border border-[#E5DFD5] flex flex-col gap-2">
                  <div className="flex justify-between font-semibold text-[#23201D]">
                    <span>Dispensing Engine</span>
                    <span className="text-blue-800">FEFO Automated Priority</span>
                  </div>
                  <div className="flex justify-between text-[#6B655D]">
                    <span>Safety Cross-Check</span>
                    <span>Contraindications & High-Yield Pearls</span>
                  </div>
                </div>
              </div>
            )}

            {activeDashboardAction === 'inventory' && (
              <div className="flex flex-col gap-3 text-xs">
                <p className="text-[#6B655D] leading-relaxed">
                  Real-time stock valuation across all active pharmaceutical inventory cost layers.
                </p>
                <div className="bg-white p-3.5 rounded-2xl border border-[#E5DFD5] flex flex-col gap-2">
                  <div className="flex justify-between font-semibold text-[#23201D]">
                    <span>Valuation Method</span>
                    <span className="text-amber-800">Perpetual Moving FIFO Cost Layers</span>
                  </div>
                  <div className="flex justify-between text-[#6B655D]">
                    <span>Total Database Nodes</span>
                    <span>{nodes.length} Items</span>
                  </div>
                </div>
              </div>
            )}

            {activeDashboardAction === 'alerts' && (
              <div className="flex flex-col gap-3 text-xs">
                <p className="text-[#6B655D] leading-relaxed">
                  Proactive shelf-life tracking. Batches nearing expiration are automatically flagged for prioritized dispensing or quarantine.
                </p>
                <div className="bg-white p-3.5 rounded-2xl border border-rose-200 flex flex-col gap-2">
                  <div className="flex justify-between font-semibold text-rose-900">
                    <span>Critical Alert Threshold</span>
                    <span>30 / 60 / 90 Days</span>
                  </div>
                  <div className="flex justify-between text-[#6B655D]">
                    <span>Quarantine Policy</span>
                    <span>Automatic Lock at T-0 Days</span>
                  </div>
                </div>
              </div>
            )}

            {activeDashboardAction === 'vitality' && (
              <div className="flex flex-col gap-3 text-xs">
                <p className="text-[#6B655D] leading-relaxed">
                  System Health & Offline Engine telemetry. Verified zero-drift between physical stock batches and financial ledgers.
                </p>
                <div className="bg-white p-3.5 rounded-2xl border border-[#E5DFD5] flex flex-col gap-2">
                  <div className="flex justify-between font-semibold text-[#23201D]">
                    <span>Room SQLite Database</span>
                    <span className="text-emerald-800">Optimal (Zero Corruption)</span>
                  </div>
                  <div className="flex justify-between text-[#6B655D]">
                    <span>Offline Status</span>
                    <span>100% Fully Functional Offline</span>
                  </div>
                </div>
              </div>
            )}

            {activeDashboardAction === 'scan' && (
              <div className="flex flex-col gap-3 text-xs">
                <div className="p-6 bg-stone-900 rounded-2xl text-center text-white flex flex-col items-center justify-center gap-3">
                  <div className="w-16 h-16 rounded-2xl border-2 border-dashed border-emerald-400 flex items-center justify-center animate-pulse">
                    <QrCode className="w-8 h-8 text-emerald-400" />
                  </div>
                  <p className="text-xs text-stone-300">
                    Point camera or scanner at commercial package barcode (GS1 / EAN-13 / DataMatrix).
                  </p>
                </div>
              </div>
            )}

            {(activeDashboardAction === 'reports' || activeDashboardAction === 'suppliers' || activeDashboardAction === 'adjustments' || activeDashboardAction === 'settings' || activeDashboardAction === 'price') && (
              <div className="flex flex-col gap-3 text-xs">
                <p className="text-[#6B655D] leading-relaxed">
                  Module configured and synchronized with the 2,212 reference taxonomy entities for facility: <strong>{facilityName}</strong>.
                </p>
                <div className="bg-white p-3.5 rounded-2xl border border-[#E5DFD5]">
                  <p className="text-[#6B655D]">
                    Operational data persists locally in standard Room SQLite and localStorage.
                  </p>
                </div>
              </div>
            )}

            <div className="flex justify-between items-center pt-3 border-t border-[#E5DFD5]">
              <button
                onClick={() => {
                  setActiveDashboardAction(null);
                  setCurrentView('products');
                }}
                className="text-xs font-semibold text-emerald-800 hover:underline flex items-center gap-1"
              >
                <span>View Products Catalog →</span>
              </button>
              <button
                onClick={() => setActiveDashboardAction(null)}
                className="px-5 py-2 rounded-xl text-xs font-semibold bg-emerald-800 hover:bg-emerald-900 text-white shadow-xs"
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
