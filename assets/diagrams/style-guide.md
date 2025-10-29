# Visual Design Style Guide

## Color Palette

### Primary Colors
- **Primary Blue**: `#2563eb` - Main system components
- **Secondary Blue**: `#3b82f6` - Supporting elements
- **Accent Blue**: `#60a5fa` - Highlights and connections

### Component Colors
- **Database**: `#059669` (Green)
- **Cache**: `#dc2626` (Red)
- **Load Balancer**: `#7c3aed` (Purple)
- **Queue/Message Broker**: `#ea580c` (Orange)
- **API Gateway**: `#0891b2` (Cyan)
- **Microservice**: `#2563eb` (Primary Blue)
- **External Service**: `#6b7280` (Gray)

### Status Colors
- **Success/Active**: `#10b981`
- **Warning**: `#f59e0b`
- **Error/Critical**: `#ef4444`
- **Info**: `#3b82f6`

### Background Colors
- **Light Background**: `#f8fafc`
- **Card Background**: `#ffffff`
- **Border**: `#e2e8f0`
- **Text Primary**: `#1e293b`
- **Text Secondary**: `#64748b`

## Typography

### Diagram Text
- **Title**: Bold, 16px
- **Component Labels**: Medium, 12px
- **Annotations**: Regular, 10px
- **Font Family**: System fonts (Arial, Helvetica, sans-serif)

## Icon Standards

### Size Guidelines
- **Large Icons**: 48x48px (main components)
- **Medium Icons**: 32x32px (sub-components)
- **Small Icons**: 16x16px (indicators, status)

### Style Guidelines
- **Line Weight**: 2px for outlines
- **Corner Radius**: 4px for rounded rectangles
- **Padding**: 8px internal padding for text elements

## Component Symbols

### Database
```
┌─────────────┐
│  Database   │
│ ┌─────────┐ │
│ │ ═══════ │ │
│ │ ═══════ │ │
│ │ ═══════ │ │
│ └─────────┘ │
└─────────────┘
```

### Cache
```
┌─────────────┐
│    Cache    │
│ ⚡ Fast     │
│   Storage   │
└─────────────┘
```

### Load Balancer
```
┌─────────────┐
│Load Balancer│
│     ⚖️      │
│ Distribute  │
└─────────────┘
```

### Message Queue
```
┌─────────────┐
│Message Queue│
│ ┌─┐ ┌─┐ ┌─┐ │
│ │M│→│M│→│M│ │
│ └─┘ └─┘ └─┘ │
└─────────────┘
```

## Diagram Layout Principles

1. **Left-to-Right Flow**: User requests flow from left to right
2. **Top-to-Bottom Hierarchy**: Higher-level components at top
3. **Consistent Spacing**: 20px minimum between components
4. **Clear Connections**: Use arrows to show data flow
5. **Grouping**: Related components should be visually grouped

## Mermaid Styling

```css
%%{init: {
  'theme': 'base',
  'themeVariables': {
    'primaryColor': '#2563eb',
    'primaryTextColor': '#1e293b',
    'primaryBorderColor': '#3b82f6',
    'lineColor': '#64748b',
    'secondaryColor': '#f8fafc',
    'tertiaryColor': '#e2e8f0'
  }
}}%%
```

## PlantUML Styling

```plantuml
!define PRIMARY_COLOR #2563eb
!define SECONDARY_COLOR #3b82f6
!define DATABASE_COLOR #059669
!define CACHE_COLOR #dc2626
!define QUEUE_COLOR #ea580c

skinparam backgroundColor #f8fafc
skinparam defaultFontColor #1e293b
skinparam defaultFontSize 12
```