import {
  DatasourceStructure as DatasourceStructureType,
  DatasourceTable,
} from "entities/Datasource";
import React, { memo, ReactElement } from "react";
import { useSelector } from "react-redux";
import { getIsFetchingDatasourceStructure } from "selectors/entitiesSelector";
import EntityPlaceholder from "../Entity/Placeholder";
import DatasourceStructure from "./DatasourceStructure";

type Props = {
  datasourceId: string;
  datasourceStructure?: DatasourceStructureType;
  step: number;
};

const Container = (props: Props) => {
  let view: ReactElement<Props> = <div />;
  const isFetchingDatasourceStructure = useSelector(
    getIsFetchingDatasourceStructure,
  );
  if (!isFetchingDatasourceStructure) {
    if (props.datasourceStructure?.tables?.length) {
      view = (
        <>
          {props.datasourceStructure.tables.map(
            (structure: DatasourceTable) => {
              return (
                <DatasourceStructure
                  datasourceId={props.datasourceId}
                  dbStructure={structure}
                  key={`${props.datasourceId}${structure.name}`}
                  step={props.step + 1}
                />
              );
            },
          )}
        </>
      );
    } else {
      view = (
        <EntityPlaceholder step={props.step + 1}>
          {props.datasourceStructure &&
          props.datasourceStructure.error &&
          props.datasourceStructure.error.message
            ? props.datasourceStructure.error.message
            : "No information available"}
        </EntityPlaceholder>
      );
    }
  }

  return view;
};

export const DatasourceStructureContainer = memo(Container);

DatasourceStructureContainer.displayName = "DatasourceStructureContainer";
