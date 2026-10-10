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
  Building,
  Edit2,
  Trash2,
  X,
  Check,
  Package,
  Layers,
  Activity,
  ShoppingCart,
  QrCode,
  FileText,
  Clock,
  Truck,
  RotateCcw,
  Sliders,
  User,
  Heart
} from 'lucide-react';

export interface TaxonomyNode {
  id: string;
  parentId: string | null;
  name: string;
  description: string | null;
}

const DEFAULT_ROOTS: TaxonomyNode[] = [
  { id: "1", parentId: null, name: "Medicines & Pharmaceuticals", description: "Reference clinical taxonomy of medicinal substances and pharmacological classes." },
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
  const [currentView, setCurrentView] = useState<'products' | 'dashboard'>('products');
  const [nodes, setNodes] = useState<TaxonomyNode[]>(DEFAULT_ROOTS);
  const [isLoading, setIsLoading] = useState(true);
  
  // Products Database Navigation Stack
  const [navStack, setNavStack] = useState<TaxonomyNode[]>([]);
  const currentParent = navStack[navStack.length - 1] || null;

  // Search state
  const [searchQuery, setSearchQuery] = useState('');
  const [isSearchOpen, setIsSearchOpen] = useState(false);

  // Dialogs
  const [showAddDialog, setShowAddDialog] = useState(false);
  const [editingNode, setEditingNode] = useState<TaxonomyNode | null>(null);
  const [deletingNode, setDeletingNode] = useState<TaxonomyNode | null>(null);
  const [selectedSubstance, setSelectedSubstance] = useState<TaxonomyNode | null>(null);

  // Form states
  const [formName, setFormName] = useState('');
  const [formDesc, setFormDesc] = useState('');

  // Dashboard Facility state (with excitability)
  const [facilityName, setFacilityName] = useState(() => {
    return localStorage.getItem('samillimed_facility_name') || 'SamilliMed Medical Centre';
  });
  const [isEditingFacility, setIsEditingFacility] = useState(false);
  const [facilityDraft, setFacilityDraft] = useState(facilityName);

  // Quick Action Dialogs for Dashboard
  const [activeDashboardAction, setActiveDashboardAction] = useState<string | null>(null);

  // Fetch full taxonomy JSON on mount
  useEffect(() => {
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

  // Search results
  const searchResults = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return [];
    return nodes.filter(n => 
      n.name.toLowerCase().includes(q) || 
      (n.description && n.description.toLowerCase().includes(q))
    ).slice(0, 50);
  }, [nodes, searchQuery]);

  // Path lookup for breadcrumbs
  const getAncestors = (nodeId: string): TaxonomyNode[] => {
    const path: TaxonomyNode[] = [];
    let curr = nodes.find(n => n.id === nodeId);
    while (curr && curr.parentId) {
      const parent = nodes.find(n => n.id === curr?.parentId);
      if (parent) {
        path.unshift(parent);
        curr = parent;
      } else {
        break;
      }
    }
    return path;
  };

  const handleSaveFacility = () => {
    const cleaned = facilityDraft.trim().slice(0, 48) || 'SamilliMed Medical Centre';
    setFacilityName(cleaned);
    setFacilityDraft(cleaned);
    localStorage.setItem('samillimed_facility_name', cleaned);
    setIsEditingFacility(false);
  };

  const handleAddNode = () => {
    if (!formName.trim()) return;
    const parentId = currentParent ? currentParent.id : null;
    const siblingCount = nodes.filter(n => n.parentId === parentId).length;
    
    // Canonical Option 2 dot-notation ID
    let candidate = parentId ? `${parentId}.${siblingCount + 1}` : `${siblingCount + 1}`;
    let offset = 1;
    while (nodes.some(n => n.id === candidate)) {
      candidate = parentId ? `${parentId}.${siblingCount + 1 + offset}` : `${siblingCount + 1 + offset}`;
      offset++;
    }

    const newNode: TaxonomyNode = {
      id: candidate,
      parentId,
      name: formName.trim(),
      description: formDesc.trim() || null
    };

    setNodes(prev => [...prev, newNode]);
    setShowAddDialog(false);
    setFormName('');
    setFormDesc('');
  };

  const handleEditNode = () => {
    if (!editingNode || !formName.trim()) return;
    setNodes(prev => prev.map(n => {
      if (n.id === editingNode.id) {
        return {
          ...n,
          name: formName.trim(),
          description: formDesc.trim() || null
        };
      }
      return n;
    }));
    setEditingNode(null);
    setFormName('');
    setFormDesc('');
  };

  const handleDeleteNode = () => {
    if (!deletingNode) return;
    // Remove node and all recursive descendants
    const idsToRemove = new Set<string>([deletingNode.id]);
    let added = true;
    while (added) {
      added = false;
      for (const n of nodes) {
        if (n.parentId && idsToRemove.has(n.parentId) && !idsToRemove.has(n.id)) {
          idsToRemove.add(n.id);
          added = true;
        }
      }
    }
    setNodes(prev => prev.filter(n => !idsToRemove.has(n.id)));
    setDeletingNode(null);
  };

  const getCategoryIcon = (id: string, isLeaf: boolean) => {
    if (isLeaf) return <Pill className="w-5 h-5 text-teal-700" />;
    switch (id) {
      case "1": return <Pill className="w-5 h-5 text-emerald-700" />;
      case "2": return <Syringe className="w-5 h-5 text-emerald-700" />;
      case "3": return <Microscope className="w-5 h-5 text-emerald-700" />;
      case "4": return <Stethoscope className="w-5 h-5 text-emerald-700" />;
      case "5": return <Bandage className="w-5 h-5 text-emerald-700" />;
      case "6": return <ShieldAlert className="w-5 h-5 text-emerald-700" />;
      case "7": return <Baby className="w-5 h-5 text-emerald-700" />;
      case "8": return <Sparkles className="w-5 h-5 text-emerald-700" />;
      case "9": return <Apple className="w-5 h-5 text-emerald-700" />;
      case "10": return <Building className="w-5 h-5 text-emerald-700" />;
      default: return <Layers className="w-5 h-5 text-emerald-700" />;
    }
  };

  return (
    <div className="min-h-screen bg-[#F3EFE6] text-[#23201D] flex flex-col font-sans select-none">
      
      {/* ========================================================================= */}
      {/* 1. SEAMLESS TOP HEADER BAR (Background-matched, seamless)                  */}
      {/* ========================================================================= */}
      <header className="px-4 py-3 bg-[#F3EFE6] border-b border-[#E5DFD5]/60 sticky top-0 z-30 flex items-center justify-between">
        <div className="flex items-center gap-3">
          {currentView === 'products' ? (
            <button
              onClick={() => {
                if (navStack.length > 0) {
                  setNavStack(prev => prev.slice(0, -1));
                } else {
                  setCurrentView('dashboard');
                }
              }}
              className="p-2 hover:bg-[#EAE2D3] rounded-full transition-colors text-[#23201D]"
              title={navStack.length > 0 ? "Back to previous level" : "Go to Dashboard"}
            >
              <ArrowLeft className="w-5 h-5" />
            </button>
          ) : (
            <button
              onClick={() => setCurrentView('products')}
              className="p-2 hover:bg-[#EAE2D3] rounded-full transition-colors text-emerald-800"
              title="Open Products Database"
            >
              <Layers className="w-5 h-5" />
            </button>
          )}

          <div>
            <h1 className="text-xl font-bold tracking-tight text-[#23201D]">
              {currentView === 'products' ? 'Products Database' : facilityName}
            </h1>
            <p className="text-xs text-[#6B655D]">
              {currentView === 'products'
                ? (currentParent ? currentParent.name : 'Reference Taxonomy & Medical Formulations')
                : 'Facility Operations & Clinical Dispensary'}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {currentView === 'products' ? (
            <>
              <button
                onClick={() => setIsSearchOpen(!isSearchOpen)}
                className={`p-2 rounded-full transition-colors ${isSearchOpen ? 'bg-[#EAE2D3] text-emerald-800' : 'hover:bg-[#EAE2D3] text-[#23201D]'}`}
                title="Search Database"
              >
                <Search className="w-5 h-5" />
              </button>
              <button
                onClick={() => {
                  setFormName('');
                  setFormDesc('');
                  setShowAddDialog(true);
                }}
                className="p-2 rounded-full hover:bg-emerald-100 text-emerald-800 transition-colors"
                title="Add Category or Substance"
              >
                <Plus className="w-5 h-5" />
              </button>
              <button
                onClick={() => setCurrentView('dashboard')}
                className="text-xs font-semibold px-3 py-1.5 rounded-lg bg-[#E5DFD5] hover:bg-[#DDD5C8] text-[#23201D] transition-colors"
              >
                Dashboard
              </button>
            </>
          ) : (
            <button
              onClick={() => setCurrentView('products')}
              className="text-xs font-semibold px-3 py-1.5 rounded-lg bg-emerald-800 hover:bg-emerald-900 text-white transition-colors flex items-center gap-1.5 shadow-sm"
            >
              <Layers className="w-4 h-4" />
              Products Database
            </button>
          )}
        </div>
      </header>

      {/* ========================================================================= */}
      {/* MAIN VIEW: PRODUCTS DATABASE HIERARCHY                                    */}
      {/* ========================================================================= */}
      {currentView === 'products' && (
        <main className="flex-1 max-w-4xl w-full mx-auto p-4 flex flex-col gap-3">
          
          {/* SEARCH FIELD */}
          {isSearchOpen && (
            <div className="relative mb-2">
              <Search className="w-4 h-4 absolute left-3 top-3 text-[#6B655D]" />
              <input
                type="text"
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                placeholder="Search classes, substances, or formulations..."
                className="w-full bg-[#FAF7F2] border border-[#E5DFD5] rounded-xl pl-9 pr-9 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-700 text-[#23201D] placeholder-[#8C8479]"
                autoFocus
              />
              {searchQuery && (
                <button
                  onClick={() => setSearchQuery('')}
                  className="absolute right-3 top-3 text-[#6B655D] hover:text-[#23201D]"
                >
                  <X className="w-4 h-4" />
                </button>
              )}
            </div>
          )}

          {/* CLINICAL BREADCRUMB TRAIL */}
          {navStack.length > 0 && !searchQuery && (
            <nav className="flex items-center gap-1.5 overflow-x-auto py-1 text-xs text-[#6B655D] no-scrollbar">
              <button
                onClick={() => setNavStack([])}
                className="hover:text-emerald-800 font-medium px-2 py-1 rounded bg-[#EBE6DC] hover:bg-[#E2DDD1] transition-colors shrink-0"
              >
                All Categories
              </button>
              {navStack.map((crumb, idx) => {
                const isLast = idx === navStack.length - 1;
                return (
                  <React.Fragment key={crumb.id}>
                    <ChevronRight className="w-3.5 h-3.5 text-[#8C8479] shrink-0" />
                    <button
                      onClick={() => setNavStack(navStack.slice(0, idx + 1))}
                      className={`px-2 py-1 rounded transition-colors shrink-0 ${
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
            <div className="flex-1 flex flex-col items-center justify-center p-12 text-[#6B655D] gap-3">
              <div className="w-8 h-8 border-3 border-emerald-700 border-t-transparent rounded-full animate-spin" />
              <p className="text-sm font-medium">Loading clinical taxonomy database...</p>
            </div>
          ) : searchQuery ? (
            /* SEARCH RESULTS MODE */
            <div className="flex flex-col gap-2">
              <p className="text-xs font-semibold uppercase tracking-wider text-[#6B655D] px-1">
                Matching Entries ({searchResults.length})
              </p>
              {searchResults.length === 0 ? (
                <div className="p-8 text-center bg-[#FAF7F2] rounded-xl border border-[#E5DFD5] text-[#6B655D]">
                  No matching categories or substances found.
                </div>
              ) : (
                searchResults.map(result => {
                  const count = childCounts[result.id] || 0;
                  const isLeaf = count === 0;
                  return (
                    <div
                      key={result.id}
                      onClick={() => {
                        const path = getAncestors(result.id);
                        setNavStack([...path, result]);
                        setSearchQuery('');
                        setIsSearchOpen(false);
                      }}
                      className="flex items-center justify-between p-3.5 bg-[#FAF7F2] hover:bg-white border border-[#E5DFD5] hover:border-emerald-700/40 rounded-xl cursor-pointer transition-all shadow-xs"
                    >
                      <div className="flex items-center gap-3">
                        <div className={`w-9 h-9 rounded-full flex items-center justify-center ${isLeaf ? 'bg-teal-50' : 'bg-emerald-50'}`}>
                          {getCategoryIcon(result.id, isLeaf)}
                        </div>
                        <div>
                          <p className="font-semibold text-sm text-[#23201D]">{result.name}</p>
                          {result.description && (
                            <p className="text-xs text-[#6B655D] line-clamp-1">{result.description}</p>
                          )}
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-[#EBE6DC] text-[#6B655D]">
                          {count > 0 ? `${count} items` : 'Substance'}
                        </span>
                        <ChevronRight className="w-4 h-4 text-[#8C8479]" />
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          ) : currentChildren.length > 0 ? (
            /* HORIZONTAL BARS LISTED DOWNWARDS (Categorization & Subcategories) */
            <div className="flex flex-col gap-2.5">
              {currentChildren.map(cat => {
                const count = childCounts[cat.id] || 0;
                const isLeaf = count === 0 && currentParent !== null;
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
                      className="flex items-center gap-3 flex-1 cursor-pointer"
                    >
                      <div className={`w-10 h-10 rounded-full flex items-center justify-center shrink-0 ${isLeaf ? 'bg-teal-50' : 'bg-emerald-50'}`}>
                        {getCategoryIcon(cat.id, isLeaf)}
                      </div>
                      <div className="min-w-0 pr-2">
                        <p className="font-semibold text-sm md:text-base text-[#23201D] group-hover:text-emerald-900 transition-colors">
                          {cat.name}
                        </p>
                        {cat.description && (
                          <p className="text-xs text-[#6B655D] line-clamp-1">{cat.description}</p>
                        )}
                      </div>
                    </div>

                    <div className="flex items-center gap-1.5 shrink-0">
                      <span className="text-xs font-bold px-2.5 py-1 rounded-full bg-[#EBE6DC] text-[#554E45]">
                        {count > 0 ? count : (isLeaf ? 'Substance' : 'Empty')}
                      </span>

                      {/* Dropdown / Edit Actions */}
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          setEditingNode(cat);
                          setFormName(cat.name);
                          setFormDesc(cat.description || '');
                        }}
                        className="p-1.5 rounded-lg text-[#8C8479] hover:text-[#23201D] hover:bg-[#EAE2D3] transition-colors"
                        title="Edit / Rename"
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
                  setFormDesc('');
                  setShowAddDialog(true);
                }}
                className="mt-2 w-full py-3 px-4 border border-dashed border-emerald-700/40 rounded-xl text-emerald-800 hover:bg-emerald-50/50 font-semibold text-sm flex items-center justify-center gap-2 transition-colors"
              >
                <Plus className="w-4 h-4" />
                {currentParent ? `Add Subcategory to ${currentParent.name}` : 'Add New Primary Category'}
              </button>
            </div>
          ) : (
            /* TERMINAL LEAF / INDIVIDUAL DRUG SUBSTANCE VIEW */
            <div className="bg-[#FAF7F2] border border-[#E5DFD5] rounded-2xl p-6 shadow-xs flex flex-col gap-4">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-3">
                  <div className="w-12 h-12 rounded-xl bg-emerald-100 flex items-center justify-center text-emerald-800">
                    <Pill className="w-6 h-6" />
                  </div>
                  <div>
                    <h2 className="text-xl font-bold text-[#23201D]">{currentParent?.name}</h2>
                    <span className="text-xs font-semibold px-2 py-0.5 rounded bg-teal-100 text-teal-800">
                      Canonical Medicinal Substance
                    </span>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    onClick={() => {
                      if (currentParent) {
                        setEditingNode(currentParent);
                        setFormName(currentParent.name);
                        setFormDesc(currentParent.description || '');
                      }
                    }}
                    className="p-2 border border-[#E5DFD5] rounded-lg hover:bg-white text-[#6B655D] hover:text-[#23201D] transition-colors"
                    title="Edit substance profile"
                  >
                    <Edit2 className="w-4 h-4" />
                  </button>
                </div>
              </div>

              {currentParent?.description && (
                <div className="bg-white/80 p-3.5 rounded-xl border border-[#E5DFD5]">
                  <p className="text-xs font-bold text-[#6B655D] uppercase tracking-wide mb-1">
                    Clinical Profile & Mechanism of Action
                  </p>
                  <p className="text-sm text-[#23201D] leading-relaxed">
                    {currentParent.description}
                  </p>
                </div>
              )}

              <div>
                <p className="text-xs font-bold text-[#6B655D] uppercase tracking-wide mb-2">
                  Registered Commercial Formulations
                </p>
                <div className="bg-white/60 rounded-xl p-4 border border-[#E5DFD5] text-center text-xs text-[#6B655D]">
                  No active brand batches currently registered in local stock under this substance.
                  <div className="mt-3">
                    <button
                      onClick={() => setCurrentView('dashboard')}
                      className="px-3 py-1.5 bg-emerald-800 text-white rounded-lg font-medium text-xs hover:bg-emerald-900 transition-colors"
                    >
                      Receive Batch via Goods Receiving
                    </button>
                  </div>
                </div>
              </div>

              <div className="pt-2 flex justify-between items-center text-xs text-[#8C8479]">
                <span>Option 2 Canonical Key: {currentParent?.id}</span>
                <button
                  onClick={() => {
                    setFormName('');
                    setFormDesc('');
                    setShowAddDialog(true);
                  }}
                  className="text-emerald-800 font-semibold hover:underline"
                >
                  + Add Specialized Derivative Form
                </button>
              </div>
            </div>
          )}
        </main>
      )}

      {/* ========================================================================= */}
      {/* SECONDARY VIEW: DASHBOARD (Preserving excitability & facility features)   */}
      {/* ========================================================================= */}
      {currentView === 'dashboard' && (
        <main className="flex-1 max-w-5xl w-full mx-auto p-4 md:p-6 flex flex-col justify-between">
          
          {/* FACILITY NAME WITH EXCITABILITY (Click to edit inline) */}
          <section className="mb-6 bg-white/70 backdrop-blur-md rounded-2xl p-5 border border-[#E5DFD5] shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div>
              <span className="text-xs font-bold tracking-wider uppercase text-emerald-800">
                Active Medical Facility Profile
              </span>
              {isEditingFacility ? (
                <div className="flex items-center gap-2 mt-1">
                  <input
                    type="text"
                    value={facilityDraft}
                    onChange={e => setFacilityDraft(e.target.value)}
                    className="border border-emerald-700 rounded-lg px-2.5 py-1 text-base font-bold bg-white focus:outline-none"
                    autoFocus
                  />
                  <button
                    onClick={handleSaveFacility}
                    className="p-1.5 bg-emerald-800 text-white rounded-lg hover:bg-emerald-900"
                  >
                    <Check className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => {
                      setFacilityDraft(facilityName);
                      setIsEditingFacility(false);
                    }}
                    className="p-1.5 bg-[#E5DFD5] text-[#23201D] rounded-lg hover:bg-[#DDD5C8]"
                  >
                    <X className="w-4 h-4" />
                  </button>
                </div>
              ) : (
                <div
                  onClick={() => setIsEditingFacility(true)}
                  className="flex items-center gap-2 cursor-pointer group mt-0.5"
                  title="Click to rename facility"
                >
                  <h2 className="text-xl md:text-2xl font-black text-[#23201D] tracking-tight group-hover:text-emerald-900 transition-colors">
                    {facilityName}
                  </h2>
                  <Edit2 className="w-4 h-4 text-[#8C8479] group-hover:text-emerald-800 opacity-60 group-hover:opacity-100 transition-opacity" />
                </div>
              )}
              <p className="text-xs text-[#6B655D] mt-0.5">
                Zero-drift FEFO inventory ledger &amp; dispensary compliance
              </p>
            </div>

            <div className="flex items-center gap-2">
              <button
                onClick={() => setCurrentView('products')}
                className="px-4 py-2 bg-emerald-800 hover:bg-emerald-900 text-white rounded-xl text-xs font-bold transition-all shadow-sm flex items-center gap-2"
              >
                <Layers className="w-4 h-4" />
                Browse Products Database
              </button>
            </div>
          </section>

          {/* 8 FROSTED GLASS TILES (Operational Functions) */}
          <section className="grid grid-cols-2 sm:grid-cols-4 gap-3 md:gap-4 mb-6">
            {[
              { id: 'receiving', title: 'Goods Receiving', icon: Truck, color: 'text-amber-800', desc: 'Scan invoice, batch intake, FEFO layers' },
              { id: 'dispensing', title: 'Dispensing', icon: Activity, color: 'text-emerald-800', desc: 'Direct patient dispensing, stock checkout' },
              { id: 'inventory', title: 'Inventory', icon: Package, color: 'text-blue-800', desc: 'Real-time stock valuation & counts' },
              { id: 'products', title: 'Products Database', icon: Layers, color: 'text-emerald-700', desc: 'Canonical taxonomy & 2,200+ drugs' },
              { id: 'alerts', title: 'Expiry Alerts', icon: Clock, color: 'text-red-700', desc: 'Proactive 30/60/90 day expiry radar' },
              { id: 'reports', title: 'Reports', icon: FileText, color: 'text-purple-800', desc: 'Dispensary margins, audit logs' },
              { id: 'suppliers', title: 'Suppliers', icon: Building, color: 'text-stone-700', desc: 'Vendor records & consignment bills' },
              { id: 'adjustments', title: 'Stock Adjustments', icon: RotateCcw, color: 'text-orange-800', desc: 'Breakage, write-off & reconcile' }
            ].map((card) => {
              const IconComp = card.icon;
              return (
                <div
                  key={card.id}
                  onClick={() => {
                    if (card.id === 'products') {
                      setCurrentView('products');
                    } else {
                      setActiveDashboardAction(card.title);
                    }
                  }}
                  className="bg-white/70 hover:bg-white backdrop-blur-xs border border-[#E5DFD5] hover:border-amber-400/80 rounded-2xl p-4 flex flex-col justify-between cursor-pointer transition-all hover:shadow-md hover:-translate-y-0.5 group min-h-[120px]"
                >
                  <div className="flex items-center justify-between">
                    <div className="w-10 h-10 rounded-xl bg-[#F3EFE6] group-hover:bg-amber-50 flex items-center justify-center transition-colors">
                      <IconComp className={`w-5 h-5 ${card.color}`} />
                    </div>
                    <ChevronRight className="w-4 h-4 text-[#8C8479] group-hover:text-[#23201D] transition-colors" />
                  </div>
                  <div>
                    <h3 className="font-bold text-sm text-[#23201D] group-hover:text-emerald-900 transition-colors">
                      {card.title}
                    </h3>
                    <p className="text-[11px] text-[#6B655D] line-clamp-1 mt-0.5">{card.desc}</p>
                  </div>
                </div>
              );
            })}
          </section>

          {/* FLOATING GLASS BOTTOM DOCK */}
          <section className="bg-white/80 backdrop-blur-md rounded-2xl p-3 border border-[#E5DFD5] flex items-center justify-around shadow-sm max-w-md mx-auto w-full">
            <button
              onClick={() => setActiveDashboardAction('Barcode Camera Scanner')}
              className="flex flex-col items-center gap-1 p-2 rounded-xl hover:bg-[#F3EFE6] text-[#23201D] transition-colors"
              title="Camera Scan Barcode"
            >
              <QrCode className="w-5 h-5 text-emerald-800" />
              <span className="text-[10px] font-semibold">Scan</span>
            </button>
            <button
              onClick={() => setActiveDashboardAction('Quick Cart / POS')}
              className="flex flex-col items-center gap-1 p-2 rounded-xl hover:bg-[#F3EFE6] text-[#23201D] transition-colors"
              title="Sales & Quick Cart"
            >
              <ShoppingCart className="w-5 h-5 text-emerald-800" />
              <span className="text-[10px] font-semibold">Cart</span>
            </button>
            <button
              onClick={() => setCurrentView('products')}
              className="flex flex-col items-center gap-1 p-2 rounded-xl bg-emerald-100 text-emerald-900 transition-colors"
              title="Database"
            >
              <Layers className="w-5 h-5" />
              <span className="text-[10px] font-bold">Taxonomy</span>
            </button>
            <button
              onClick={() => setActiveDashboardAction('Facility Profile & Preferences')}
              className="flex flex-col items-center gap-1 p-2 rounded-xl hover:bg-[#F3EFE6] text-[#23201D] transition-colors"
              title="Preferences"
            >
              <Sliders className="w-5 h-5 text-emerald-800" />
              <span className="text-[10px] font-semibold">Settings</span>
            </button>
          </section>
        </main>
      )}

      {/* ========================================================================= */}
      {/* MODAL DIALOGS FOR FULL USER EDITABILITY                                   */}
      {/* ========================================================================= */}

      {/* ADD CATEGORY / SUBSTANCE DIALOG */}
      {showAddDialog && (
        <div className="fixed inset-0 bg-black/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-[#FAF7F2] border border-[#E5DFD5] rounded-2xl max-w-md w-full p-6 shadow-xl flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <h3 className="font-bold text-lg text-[#23201D]">
                {currentParent ? `Add to ${currentParent.name}` : 'New Primary Category'}
              </h3>
              <button onClick={() => setShowAddDialog(false)} className="text-[#8C8479] hover:text-[#23201D]">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="flex flex-col gap-3">
              <div>
                <label className="text-xs font-bold text-[#6B655D] block mb-1">
                  Name / Title
                </label>
                <input
                  type="text"
                  value={formName}
                  onChange={e => setFormName(e.target.value)}
                  placeholder="e.g. Beta-blockers or Atenolol"
                  className="w-full bg-white border border-[#E5DFD5] rounded-xl px-3 py-2 text-sm text-[#23201D] focus:outline-none focus:ring-2 focus:ring-emerald-700"
                  autoFocus
                />
              </div>

              <div>
                <label className="text-xs font-bold text-[#6B655D] block mb-1">
                  Clinical Description / Mechanism (Optional)
                </label>
                <textarea
                  value={formDesc}
                  onChange={e => setFormDesc(e.target.value)}
                  placeholder="e.g. Selective β1 adrenergic antagonist for hypertension..."
                  rows={3}
                  className="w-full bg-white border border-[#E5DFD5] rounded-xl px-3 py-2 text-sm text-[#23201D] focus:outline-none focus:ring-2 focus:ring-emerald-700 resize-none"
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setShowAddDialog(false)}
                className="px-4 py-2 rounded-xl text-xs font-semibold hover:bg-[#EAE2D3] text-[#23201D]"
              >
                Cancel
              </button>
              <button
                onClick={handleAddNode}
                className="px-4 py-2 rounded-xl text-xs font-semibold bg-emerald-800 hover:bg-emerald-900 text-white transition-colors"
              >
                Create Entry
              </button>
            </div>
          </div>
        </div>
      )}

      {/* EDIT CATEGORY / SUBSTANCE DIALOG */}
      {editingNode && (
        <div className="fixed inset-0 bg-black/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-[#FAF7F2] border border-[#E5DFD5] rounded-2xl max-w-md w-full p-6 shadow-xl flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <h3 className="font-bold text-lg text-[#23201D]">Edit Category / Substance</h3>
              <button onClick={() => setEditingNode(null)} className="text-[#8C8479] hover:text-[#23201D]">
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="flex flex-col gap-3">
              <div>
                <label className="text-xs font-bold text-[#6B655D] block mb-1">
                  Name / Title
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
                  Clinical Description / Mechanism
                </label>
                <textarea
                  value={formDesc}
                  onChange={e => setFormDesc(e.target.value)}
                  rows={3}
                  className="w-full bg-white border border-[#E5DFD5] rounded-xl px-3 py-2 text-sm text-[#23201D] focus:outline-none focus:ring-2 focus:ring-emerald-700 resize-none"
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setEditingNode(null)}
                className="px-4 py-2 rounded-xl text-xs font-semibold hover:bg-[#EAE2D3] text-[#23201D]"
              >
                Cancel
              </button>
              <button
                onClick={handleEditNode}
                className="px-4 py-2 rounded-xl text-xs font-semibold bg-emerald-800 hover:bg-emerald-900 text-white transition-colors"
              >
                Save Changes
              </button>
            </div>
          </div>
        </div>
      )}

      {/* DELETE / ARCHIVE CONFIRMATION DIALOG */}
      {deletingNode && (
        <div className="fixed inset-0 bg-black/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-[#FAF7F2] border border-[#E5DFD5] rounded-2xl max-w-sm w-full p-6 shadow-xl flex flex-col gap-4">
            <h3 className="font-bold text-lg text-red-700">Delete Entry</h3>
            <p className="text-sm text-[#23201D]">
              Are you sure you want to remove <span className="font-bold">{deletingNode.name}</span> and any sub-elements from the local database?
            </p>
            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => setDeletingNode(null)}
                className="px-4 py-2 rounded-xl text-xs font-semibold hover:bg-[#EAE2D3] text-[#23201D]"
              >
                Cancel
              </button>
              <button
                onClick={handleDeleteNode}
                className="px-4 py-2 rounded-xl text-xs font-semibold bg-red-700 hover:bg-red-800 text-white transition-colors"
              >
                Confirm Delete
              </button>
            </div>
          </div>
        </div>
      )}

      {/* SUBSTANCE DETAIL MODAL */}
      {selectedSubstance && (
        <div className="fixed inset-0 bg-black/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-[#FAF7F2] border border-[#E5DFD5] rounded-2xl max-w-md w-full p-6 shadow-xl flex flex-col gap-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-full bg-teal-100 flex items-center justify-center text-teal-800">
                  <Pill className="w-4 h-4" />
                </div>
                <div>
                  <h3 className="font-bold text-base text-[#23201D]">{selectedSubstance.name}</h3>
                  <span className="text-[10px] font-bold text-teal-800 uppercase tracking-wide">
                    Individual Medicinal Substance
                  </span>
                </div>
              </div>
              <button onClick={() => setSelectedSubstance(null)} className="text-[#8C8479] hover:text-[#23201D]">
                <X className="w-5 h-5" />
              </button>
            </div>

            {selectedSubstance.description ? (
              <div className="bg-white p-3 rounded-xl border border-[#E5DFD5]">
                <p className="text-xs font-bold text-[#6B655D] mb-1">Clinical Profile</p>
                <p className="text-xs text-[#23201D] leading-relaxed">{selectedSubstance.description}</p>
              </div>
            ) : (
              <p className="text-xs text-[#6B655D] italic">No detailed pharmacological annotation supplied yet.</p>
            )}

            <div className="flex justify-between items-center pt-2">
              <button
                onClick={() => {
                  const node = selectedSubstance;
                  setSelectedSubstance(null);
                  setEditingNode(node);
                  setFormName(node.name);
                  setFormDesc(node.description || '');
                }}
                className="text-xs font-semibold text-emerald-800 hover:underline flex items-center gap-1"
              >
                <Edit2 className="w-3.5 h-3.5" />
                Edit Substance Info
              </button>
              <button
                onClick={() => setSelectedSubstance(null)}
                className="px-4 py-2 rounded-xl text-xs font-semibold bg-emerald-800 hover:bg-emerald-900 text-white"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* DASHBOARD ACTION PREVIEW MODAL */}
      {activeDashboardAction && (
        <div className="fixed inset-0 bg-black/40 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-[#FAF7F2] border border-[#E5DFD5] rounded-2xl max-w-sm w-full p-6 shadow-xl flex flex-col gap-3">
            <h3 className="font-bold text-base text-[#23201D]">{activeDashboardAction}</h3>
            <p className="text-xs text-[#6B655D]">
              This operational function is connected to the SamilliMed dispensary engine on Android device. In the web workstation, use the Products Database to browse and curate taxonomy.
            </p>
            <div className="flex justify-end pt-2">
              <button
                onClick={() => setActiveDashboardAction(null)}
                className="px-4 py-2 rounded-xl text-xs font-semibold bg-emerald-800 hover:bg-emerald-900 text-white"
              >
                Got it
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}
